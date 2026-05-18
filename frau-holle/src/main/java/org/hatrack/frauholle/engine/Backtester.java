package org.hatrack.frauholle.engine;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.error.BacktestException;
import org.hatrack.frauholle.error.BacktestInternalException;
import org.hatrack.frauholle.error.InvalidExplicitFillException;
import org.hatrack.frauholle.error.SignalGenerationException;
import org.hatrack.frauholle.internal.MetricsCalculator;
import org.hatrack.frauholle.internal.TimeframeInference;
import org.hatrack.frauholle.model.BarContext;
import org.hatrack.frauholle.model.Direction;
import org.hatrack.frauholle.model.EquityPoint;
import org.hatrack.frauholle.model.Position;
import org.hatrack.frauholle.model.Signal;
import org.hatrack.frauholle.model.Trade;
import org.hatrack.frauholle.result.BacktestDiagnostics;
import org.hatrack.frauholle.result.BacktestMetrics;
import org.hatrack.frauholle.result.BacktestResult;
import org.hatrack.frauholle.spec.BacktestSpec;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Event-driven, single-threaded backtester. A signal generated at bar t is
 * filled at the open of bar t+1; an open position at the last bar is
 * marked-to-market, never force-closed. Frictionless.
 *
 * <p>v1.1: a {@code ClosePositionAtPrice} signal closes the open position at
 * the explicit price and time it carries, rather than at the next bar open.
 * All v1 signal variants behave exactly as before.
 */
public final class Backtester {

    private static final MathContext MC = MathContext.DECIMAL64;

    public BacktestResult run(BacktestSpec spec) throws BacktestException {
        Objects.requireNonNull(spec, "spec");
        try {
            return simulate(spec);
        } catch (BacktestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BacktestInternalException(e);
        }
    }

    private BacktestResult simulate(BacktestSpec spec) throws BacktestException {
        List<OHLCBar> series = spec.series();
        BigDecimal cash = spec.initialCash();
        Position position = null;
        List<Trade> trades = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>(series.size());

        int ignoredBuys = 0;
        int ignoredSells = 0;
        int noOpCloses = 0;
        int unfilledAtEnd = 0;
        int forcedClosesAtExplicitPrice = 0;

        Signal pending = null;

        for (int t = 0; t < series.size(); t++) {
            OHLCBar bar = series.get(t);

            // 1. fill the signal pending from bar t-1 at this bar's open
            if (pending != null) {
                switch (pending) {
                    case Signal.Hold ignored -> { }
                    case Signal.Buy buy -> {
                        if (position != null) {
                            ignoredBuys++;
                        } else {
                            position = new Position(Direction.LONG, buy.quantity(),
                                    bar.time(), bar.open());
                        }
                    }
                    case Signal.Sell sell -> {
                        if (position != null) {
                            ignoredSells++;
                        } else {
                            position = new Position(Direction.SHORT, sell.quantity(),
                                    bar.time(), bar.open());
                        }
                    }
                    case Signal.ClosePosition ignored -> {
                        if (position == null) {
                            noOpCloses++;
                        } else {
                            trades.add(closeTrade(position, bar.time(), bar.open()));
                            cash = cash.add(positionValue(position, bar.open()), MC);
                            position = null;
                        }
                    }
                    case Signal.ClosePositionAtPrice explicit -> {
                        var signalBarTime = series.get(t - 1).time();
                        if (!explicit.fillTime().isAfter(signalBarTime)
                                || !explicit.fillTime().isBefore(bar.time())) {
                            throw new InvalidExplicitFillException(explicit.fillTime(), bar.time());
                        }
                        if (position == null) {
                            noOpCloses++;
                        } else {
                            trades.add(closeTrade(position, explicit.fillTime(), explicit.price()));
                            cash = cash.add(positionValue(position, explicit.price()), MC);
                            position = null;
                            forcedClosesAtExplicitPrice++;
                        }
                    }
                }
                pending = null;
            }

            // 2. generate the signal for bar t
            BigDecimal referencePrice = t > 0 ? series.get(t - 1).close() : bar.open();
            BigDecimal currentEquity = cash.add(positionValue(position, referencePrice), MC);
            BarContext context = new BarContext(bar, series.subList(0, t),
                    Optional.ofNullable(position), cash, currentEquity, t);
            Signal signal;
            try {
                signal = spec.signalGenerator().generate(context);
            } catch (SignalGenerationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new SignalGenerationException(t, e);
            }
            if (signal == null) {
                throw new SignalGenerationException(t,
                        new NullPointerException("SignalGenerator returned a null Signal"));
            }

            // 3. record the equity point for bar t (position marked at the close)
            BigDecimal positionValue = positionValue(position, bar.close());
            equityCurve.add(new EquityPoint(bar.time(), cash.add(positionValue, MC),
                    cash, positionValue));

            // 4. the signal fills at bar t+1
            pending = signal;
        }

        if (pending instanceof Signal.Buy || pending instanceof Signal.Sell) {
            unfilledAtEnd++;
        }

        BigDecimal periodsPerYear = TimeframeInference.periodsPerYear(
                series.stream().map(OHLCBar::time).toList()).orElseThrow();
        BacktestMetrics metrics = MetricsCalculator.compute(equityCurve, trades, periodsPerYear);
        BacktestDiagnostics diagnostics = new BacktestDiagnostics(
                ignoredBuys, ignoredSells, noOpCloses, unfilledAtEnd, forcedClosesAtExplicitPrice);
        return new BacktestResult(metrics, trades, equityCurve,
                Optional.ofNullable(position), diagnostics);
    }

    private static BigDecimal positionValue(Position position, BigDecimal price) {
        if (position == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal move = position.direction() == Direction.LONG
                ? price.subtract(position.entryPrice(), MC)
                : position.entryPrice().subtract(price, MC);
        return position.quantity().multiply(move, MC);
    }

    private static Trade closeTrade(Position position, java.time.Instant exitTime,
                                    BigDecimal exitPrice) {
        BigDecimal pnl = positionValue(position, exitPrice);
        BigDecimal entryCapital = position.quantity().multiply(position.entryPrice(), MC);
        BigDecimal pnlPercent = entryCapital.signum() == 0
                ? BigDecimal.ZERO
                : pnl.divide(entryCapital, MC);
        return new Trade(position.direction(), position.quantity(), position.entryTime(),
                position.entryPrice(), exitTime, exitPrice, pnl, pnlPercent);
    }
}
