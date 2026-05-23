package org.hatrack.nachtkrapp;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotPointVariant;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.detector.DetectionResult;
import org.hatrack.nachtkrapp.detector.PatternDetector;
import org.hatrack.nachtkrapp.detector.RuleBasedPatternDetector;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.rule.MAType;
import org.hatrack.nachtkrapp.spec.DetectionSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code PatternDetector} contract requires the same instance to be safe to
 * call concurrently. The Cucumber suite has one concurrent scenario; this
 * stresses it harder — many threads, a mixed rule set spanning every
 * indicator-driven family (MA, MA-vs-MA, RSI, pivots) — and asserts every
 * parallel result equals the serial baseline. Pure JUnit (a stress harness, not
 * a behavioral scenario).
 */
class PatternDetectorConcurrencyTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    private static OHLCSeries series(int n) {
        List<OHLCBar> bars = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 2.1 : -1.2) + (i % 7) * 0.4;
            double high = Math.max(open, close) + 1.3;
            double low = Math.min(open, close) - 1.1;
            bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    bd(open), bd(high), bd(low), bd(close), Optional.empty()));
            price = close;
        }
        return new OHLCSeries(bars);
    }

    private static DetectionSpec mixedSpec() throws Exception {
        return DetectionSpec.builder()
                .withSeries(series(60))
                .addRule(new DetectionRule.PriceVsMARule(MAType.SMA, 20, PriceSource.CLOSE))
                .addRule(new DetectionRule.RSIThresholdRule(14, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE))
                .addRule(new DetectionRule.MAVsMARule(MAType.SMA, 10, MAType.SMA, 30, PriceSource.CLOSE))
                .addRule(new DetectionRule.PivotPointRule(Timeframe.fromWire("1d"),
                        PivotPointVariant.STANDARD, PriceSource.CLOSE))
                .build();
    }

    @Test
    void sharedDetectorUnderManyThreadsMatchesSerialBaseline() throws Exception {
        DetectionSpec spec = mixedSpec();
        PatternDetector detector = new RuleBasedPatternDetector();
        DetectionResult baseline = detector.detect(spec);

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<DetectionResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<DetectionResult> task = () -> detector.detect(spec);
                futures.add(pool.submit(task));
            }
            for (Future<DetectionResult> future : futures) {
                assertEquals(baseline, future.get(),
                        "a concurrent detect() result diverged from the serial baseline");
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", d));
    }
}
