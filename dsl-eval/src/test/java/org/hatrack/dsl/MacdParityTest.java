package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.indicators.Indicators;
import org.hatrack.indicators.MacdResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parity port of wichtelm-app's {@code macd-evaluation.feature} /
 * {@code MacdEvaluationSteps}. Locks the promoted evaluator's MACD resolution to
 * the {@code indicators} module so the shared module cannot drift from the
 * behaviour wichtelm relied on before it delegated here.
 */
class MacdParityTest {

    /** Deterministic 60-bar series: gentle drift + wave so line and signal diverge. */
    private static List<OHLCBar> series(int barCount) {
        List<OHLCBar> bars = new ArrayList<>(barCount);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < barCount; i++) {
            double price = 100 + i * 0.5 + 8 * Math.sin(i / 5.0);
            BigDecimal close = BigDecimal.valueOf(price);
            bars.add(new OHLCBar(start.plus(Duration.ofHours(i)),
                    close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE),
                    close, Optional.empty()));
        }
        return bars;
    }

    private final List<OHLCBar> bars = series(60);

    private BigDecimal evaluate(String expression) {
        OHLCBar bar = bars.getLast();
        long index = bars.size() - 1;
        BarIndicatorSource indicators =
                new BarIndicatorSource(bars, "macd-eval", bar.time(), index);
        ExpressionEvaluator evaluator =
                new ExpressionEvaluator("macd-eval", bar.time(), index);
        ExpressionEvaluator.Scope scope = new ExpressionEvaluator.Scope(
                id -> {
                    throw new AssertionError("unexpected bare identifier: " + id);
                },
                indicators);
        return evaluator.arithmetic(expression, scope);
    }

    private List<BigDecimal> closes() {
        return bars.stream().map(OHLCBar::close).toList();
    }

    private BigDecimal lastComponent(String component) {
        MacdResult macd = Indicators.macd(closes(), 12, 26, 9);
        BigDecimal[] line = switch (component) {
            case "line" -> macd.macdLine();
            case "signal" -> macd.signalLine();
            case "histogram" -> macd.histogram();
            default -> throw new AssertionError(component);
        };
        return line[line.length - 1];
    }

    @Test
    void macdLineResolvesToIndicatorsModuleLine() {
        assertEquals(0, evaluate("macd_line(12, 26, 9)").compareTo(lastComponent("line")));
    }

    @Test
    void macdSignalResolvesToIndicatorsModuleSignal() {
        assertEquals(0, evaluate("macd_signal(12, 26, 9)").compareTo(lastComponent("signal")));
    }

    @Test
    void macdHistogramEqualsLineMinusSignal() {
        BigDecimal histogram = evaluate("macd_histogram(12, 26, 9)");
        BigDecimal lineMinusSignal =
                evaluate("macd_line(12, 26, 9) - macd_signal(12, 26, 9)");
        assertEquals(0, histogram.compareTo(lineMinusSignal));
        assertEquals(0, histogram.compareTo(lastComponent("histogram")));
    }
}
