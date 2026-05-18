package org.hatrack.frauholle.internal;

import org.hatrack.frauholle.model.EquityPoint;
import org.hatrack.frauholle.model.Trade;
import org.hatrack.frauholle.result.BacktestMetrics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the ten core {@link BacktestMetrics} from an equity curve and a
 * trade list (frau-holle/CLAUDE.md section 3). All arithmetic uses
 * {@code MathContext.DECIMAL64}.
 */
public final class MetricsCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal SECONDS_PER_YEAR = new BigDecimal("31536000");

    private MetricsCalculator() {
    }

    public static BacktestMetrics compute(List<EquityPoint> equityCurve, List<Trade> trades,
                                          BigDecimal periodsPerYear) {
        BigDecimal initialEquity = equityCurve.get(0).equity();
        BigDecimal finalEquity = equityCurve.get(equityCurve.size() - 1).equity();
        BigDecimal totalReturn = finalEquity.subtract(initialEquity, MC).divide(initialEquity, MC);

        int numTrades = trades.size();
        BigDecimal winRate = winRate(trades, numTrades);
        BigDecimal maxDrawdown = maxDrawdown(equityCurve);

        List<BigDecimal> returns = barReturns(equityCurve);
        BigDecimal sqrtPeriods = periodsPerYear.sqrt(MC);
        BigDecimal sharpe = sharpe(returns, sqrtPeriods);
        BigDecimal sortino = sortino(returns, sqrtPeriods);

        BigDecimal profitFactor = profitFactor(trades);
        BigDecimal avgWin = averagePnl(trades, true);
        BigDecimal avgLoss = averagePnl(trades, false);
        BigDecimal calmar = calmar(totalReturn, maxDrawdown, equityCurve);

        return new BacktestMetrics(totalReturn, winRate, numTrades, maxDrawdown,
                sharpe, sortino, profitFactor, avgWin, avgLoss, calmar);
    }

    private static BigDecimal winRate(List<Trade> trades, int numTrades) {
        if (numTrades == 0) {
            return BigDecimal.ZERO;
        }
        long wins = trades.stream().filter(t -> t.pnl().signum() > 0).count();
        return new BigDecimal(wins).divide(new BigDecimal(numTrades), MC);
    }

    private static BigDecimal maxDrawdown(List<EquityPoint> equityCurve) {
        BigDecimal peak = equityCurve.get(0).equity();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (EquityPoint point : equityCurve) {
            BigDecimal equity = point.equity();
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = peak.subtract(equity, MC).divide(peak, MC);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        return maxDrawdown;
    }

    private static List<BigDecimal> barReturns(List<EquityPoint> equityCurve) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            BigDecimal previous = equityCurve.get(i - 1).equity();
            BigDecimal current = equityCurve.get(i).equity();
            if (previous.signum() != 0) {
                returns.add(current.subtract(previous, MC).divide(previous, MC));
            }
        }
        return returns;
    }

    private static BigDecimal sharpe(List<BigDecimal> returns, BigDecimal sqrtPeriods) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = mean(returns);
        BigDecimal stdDev = populationStdDev(returns, mean);
        if (stdDev.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return mean.divide(stdDev, MC).multiply(sqrtPeriods, MC);
    }

    private static BigDecimal sortino(List<BigDecimal> returns, BigDecimal sqrtPeriods) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> negatives = returns.stream().filter(r -> r.signum() < 0).toList();
        if (negatives.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal downsideStdDev = populationStdDev(negatives, mean(negatives));
        if (downsideStdDev.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return mean(returns).divide(downsideStdDev, MC).multiply(sqrtPeriods, MC);
    }

    private static BigDecimal profitFactor(List<Trade> trades) {
        BigDecimal positive = BigDecimal.ZERO;
        BigDecimal negative = BigDecimal.ZERO;
        for (Trade trade : trades) {
            if (trade.pnl().signum() > 0) {
                positive = positive.add(trade.pnl(), MC);
            } else if (trade.pnl().signum() < 0) {
                negative = negative.add(trade.pnl(), MC);
            }
        }
        if (negative.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return positive.divide(negative.abs(), MC);
    }

    private static BigDecimal averagePnl(List<Trade> trades, boolean winners) {
        List<BigDecimal> pnls = trades.stream()
                .map(Trade::pnl)
                .filter(pnl -> winners ? pnl.signum() > 0 : pnl.signum() < 0)
                .toList();
        return pnls.isEmpty() ? BigDecimal.ZERO : mean(pnls);
    }

    private static BigDecimal calmar(BigDecimal totalReturn, BigDecimal maxDrawdown,
                                     List<EquityPoint> equityCurve) {
        if (maxDrawdown.signum() == 0) {
            return BigDecimal.ZERO;
        }
        long seconds = Duration.between(equityCurve.get(0).time(),
                equityCurve.get(equityCurve.size() - 1).time()).getSeconds();
        if (seconds <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal yearsCovered = new BigDecimal(seconds).divide(SECONDS_PER_YEAR, MC);
        BigDecimal annualizedReturn = totalReturn.divide(yearsCovered, MC);
        return annualizedReturn.divide(maxDrawdown, MC);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value, MC);
        }
        return sum.divide(new BigDecimal(values.size()), MC);
    }

    private static BigDecimal populationStdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal deviation = value.subtract(mean, MC);
            sumSquares = sumSquares.add(deviation.multiply(deviation, MC), MC);
        }
        return sumSquares.divide(new BigDecimal(values.size()), MC).sqrt(MC);
    }
}
