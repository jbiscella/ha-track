package org.hatrack.frauholle.result;

import java.math.BigDecimal;
import java.util.Objects;

/** The ten core backtest metrics (frau-holle/CLAUDE.md section 3). */
public record BacktestMetrics(BigDecimal totalReturn, BigDecimal winRate, int numTrades,
                              BigDecimal maxDrawdown, BigDecimal sharpeRatio,
                              BigDecimal sortinoRatio, BigDecimal profitFactor,
                              BigDecimal avgWin, BigDecimal avgLoss, BigDecimal calmarRatio) {

    public BacktestMetrics {
        Objects.requireNonNull(totalReturn, "totalReturn");
        Objects.requireNonNull(winRate, "winRate");
        Objects.requireNonNull(maxDrawdown, "maxDrawdown");
        Objects.requireNonNull(sharpeRatio, "sharpeRatio");
        Objects.requireNonNull(sortinoRatio, "sortinoRatio");
        Objects.requireNonNull(profitFactor, "profitFactor");
        Objects.requireNonNull(avgWin, "avgWin");
        Objects.requireNonNull(avgLoss, "avgLoss");
        Objects.requireNonNull(calmarRatio, "calmarRatio");
    }
}
