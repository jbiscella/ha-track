package org.hatrack.frauholle.result;

import org.hatrack.frauholle.model.EquityPoint;
import org.hatrack.frauholle.model.Position;
import org.hatrack.frauholle.model.Trade;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The complete outcome of a backtest run. */
public record BacktestResult(BacktestMetrics metrics, List<Trade> trades,
                             List<EquityPoint> equityCurve, Optional<Position> openPositionAtEnd,
                             BacktestDiagnostics diagnostics) {

    public BacktestResult {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(openPositionAtEnd, "openPositionAtEnd");
        Objects.requireNonNull(diagnostics, "diagnostics");
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
    }
}
