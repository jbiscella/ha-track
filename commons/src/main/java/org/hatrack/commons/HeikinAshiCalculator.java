package org.hatrack.commons;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class HeikinAshiCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal FOUR = new BigDecimal("4");

    private HeikinAshiCalculator() {
    }

    public static HABar compute(Optional<HABar> previous, OHLCBar ohlc) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(ohlc, "ohlc");

        BigDecimal haClose = ohlc.open()
                .add(ohlc.high(), MC)
                .add(ohlc.low(), MC)
                .add(ohlc.close(), MC)
                .divide(FOUR, MC);

        BigDecimal haOpen = previous
                .map(p -> p.haOpen().add(p.haClose(), MC).divide(TWO, MC))
                .orElseGet(() -> ohlc.open().add(ohlc.close(), MC).divide(TWO, MC));

        BigDecimal haHigh = ohlc.high().max(haOpen).max(haClose);
        BigDecimal haLow = ohlc.low().min(haOpen).min(haClose);

        return new HABar(ohlc.time(), haOpen, haHigh, haLow, haClose);
    }

    public static List<HABar> computeChain(Optional<HABar> previous, List<OHLCBar> ohlcs) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(ohlcs, "ohlcs");

        List<HABar> result = new ArrayList<>(ohlcs.size());
        Optional<HABar> prev = previous;
        for (OHLCBar ohlc : ohlcs) {
            HABar ha = compute(prev, ohlc);
            result.add(ha);
            prev = Optional.of(ha);
        }
        return List.copyOf(result);
    }
}
