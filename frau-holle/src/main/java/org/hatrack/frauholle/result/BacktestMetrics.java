package org.hatrack.frauholle.result;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The ten core backtest metrics (frau-holle/CLAUDE.md section 3), plus the exact
 * winning/losing trade counts (§18).
 */
public record BacktestMetrics(BigDecimal totalReturn, BigDecimal winRate, int numTrades,
                              BigDecimal maxDrawdown, BigDecimal sharpeRatio,
                              BigDecimal sortinoRatio, BigDecimal profitFactor,
                              BigDecimal avgWin, BigDecimal avgLoss, BigDecimal calmarRatio,
                              int winningTrades) {

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
        if (winningTrades < 0) {
            throw new IllegalArgumentException("winningTrades must be >= 0, was " + winningTrades);
        }
        if (winningTrades > numTrades) {
            throw new IllegalArgumentException("winningTrades (" + winningTrades
                    + ") must be <= numTrades (" + numTrades + ")");
        }
    }

    /**
     * Backward-compatible constructor preserving the pre-0.55.0-alpha 10-argument
     * shape. It approximates {@code winningTrades} from {@code round(winRate ×
     * numTrades)} — the only signal available without the trade list — so the
     * exact-count invariant cannot be honored on this path. The engine populates
     * the exact count via the canonical constructor; this overload exists only so
     * code compiled against the old signature keeps working.
     */
    public BacktestMetrics(BigDecimal totalReturn, BigDecimal winRate, int numTrades,
                           BigDecimal maxDrawdown, BigDecimal sharpeRatio,
                           BigDecimal sortinoRatio, BigDecimal profitFactor,
                           BigDecimal avgWin, BigDecimal avgLoss, BigDecimal calmarRatio) {
        this(totalReturn, winRate, numTrades, maxDrawdown, sharpeRatio, sortinoRatio,
                profitFactor, avgWin, avgLoss, calmarRatio,
                approximateWinningTrades(winRate, numTrades));
    }

    private static int approximateWinningTrades(BigDecimal winRate, int numTrades) {
        if (winRate == null || numTrades <= 0) {
            return 0;
        }
        int wins = winRate.multiply(new BigDecimal(numTrades), MathContext.DECIMAL64)
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(numTrades, wins));
    }

    /**
     * Number of losing trades: {@code numTrades() − winningTrades()}. Break-even
     * trades ({@code pnl == 0}) are bucketed here — consistent with {@code winRate},
     * whose numerator counts only {@code pnl > 0} — so
     * {@code winningTrades() + losingTrades() == numTrades()} holds exactly.
     */
    public int losingTrades() {
        return numTrades - winningTrades;
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
     * trade where {@code pnl &gt; 0}. It also returns {@code BigDecimal.ZERO}
     * when there are no winning trades: the numerator is then zero, so the
     * quotient is genuinely zero. See frau-holle/CLAUDE.md §3 for the
     * canonical specification.
     */
    public BigDecimal profitFactor() {
        return profitFactor;
    }
}
