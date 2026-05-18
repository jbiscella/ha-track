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

    /**
     * The profit factor: the sum of winning trade PnL divided by the absolute
     * sum of losing trade PnL.
     *
     * <p>Returns {@code BigDecimal.ZERO} when there are no losing trades. This
     * is a deliberate sentinel value meaning "undefined" (infinite profit
     * factor cannot be represented as {@code BigDecimal}). Consumers MUST
     * interpret 0 as undefined and NOT as "zero profit factor" — a true zero
     * would require winning trades summing to zero, which is impossible for a
     * trade where {@code pnl &gt; 0}. See frau-holle/CLAUDE.md §3 for the
     * canonical specification.
     */
    public BigDecimal profitFactor() {
        return profitFactor;
    }
}
