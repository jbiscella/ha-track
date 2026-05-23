package org.hatrack.frauholle;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.engine.Backtester;
import org.hatrack.frauholle.model.Signal;
import org.hatrack.frauholle.model.Trade;
import org.hatrack.frauholle.port.SignalGenerator;
import org.hatrack.frauholle.result.BacktestResult;
import org.hatrack.frauholle.spec.BacktestSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit lookahead-safety for the backtester: a fill/trade that completes at
 * or before bar T must be identical whether the series is the full window or
 * truncated at T. Fills use only the next bar's open, so truncating away later
 * bars cannot change an already-completed trade. The Cucumber suite proves the
 * fill-at-t+1 rule directly; this proves the invariant end-to-end via the
 * full-vs-truncated equivalence (the canonical no-lookahead check). Pure JUnit:
 * it runs two backtests and diffs the trade list.
 */
class BacktestLookaheadTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    /** Buy 1 at bar 5 (fills at bar 6 open), close at bar 10 (fills at bar 11 open). */
    private static final SignalGenerator STRATEGY = context -> switch (context.barIndex()) {
        case 5 -> new Signal.Buy(BigDecimal.ONE);
        case 10 -> new Signal.ClosePosition();
        default -> new Signal.Hold();
    };

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 2 == 0 ? 1.5 : -0.7);
            double high = Math.max(open, close) + 0.9;
            double low = Math.min(open, close) - 0.8;
            out.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    bd(open), bd(high), bd(low), bd(close), Optional.empty()));
            price = close;
        }
        return out;
    }

    private static BacktestResult run(List<OHLCBar> series) {
        try {
            BacktestSpec spec = BacktestSpec.builder()
                    .withSeries(series)
                    .withSignalGenerator(STRATEGY)
                    .withInitialCash(new BigDecimal("100000"))
                    .build();
            return new Backtester().run(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void completedTradeIsIdenticalOnFullAndTruncatedSeries() {
        // The single round-trip (entry fill at bar 6, exit fill at bar 11) lies
        // entirely within the first 15 bars, so truncating away bars 15..19 must
        // leave it byte-identical.
        List<OHLCBar> full = bars(20);
        BacktestResult fullResult = run(full);
        BacktestResult truncatedResult = run(full.subList(0, 15));

        assertEquals(1, fullResult.trades().size(), "full run should have one closed trade");
        assertEquals(1, truncatedResult.trades().size(), "truncated run should have the same trade");

        Trade f = fullResult.trades().get(0);
        Trade t = truncatedResult.trades().get(0);
        assertEquals(f.entryTime(), t.entryTime(), "entryTime");
        assertEquals(f.exitTime(), t.exitTime(), "exitTime");
        assertEquals(0, f.entryPrice().compareTo(t.entryPrice()), "entryPrice");
        assertEquals(0, f.exitPrice().compareTo(t.exitPrice()), "exitPrice");
        assertEquals(0, f.pnl().compareTo(t.pnl()), "pnl");
    }

    @Test
    void entryFillsAtNextBarOpenNotTheSignalBar() {
        List<OHLCBar> series = bars(20);
        Trade trade = run(series).trades().get(0);
        // signal at bar 5 -> fill at bar 6
        assertEquals(series.get(6).time(), trade.entryTime(), "entry fills at bar 6 time");
        assertEquals(0, series.get(6).open().compareTo(trade.entryPrice()), "entry at bar 6 open");
        assertTrue(trade.entryTime().isAfter(series.get(5).time()), "entry is strictly after the signal bar");
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", d));
    }
}
