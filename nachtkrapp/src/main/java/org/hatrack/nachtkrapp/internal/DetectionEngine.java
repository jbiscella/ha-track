package org.hatrack.nachtkrapp.internal;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCAggregator;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotLevel;
import org.hatrack.commons.PivotLevels;
import org.hatrack.commons.PivotPoints;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Series;
import org.hatrack.commons.Timeframe;
import org.hatrack.indicators.Indicators;
import org.hatrack.indicators.MacdResult;
import org.hatrack.nachtkrapp.match.PatternMatch;
import org.hatrack.nachtkrapp.match.PatternMatch.HABearishReversal;
import org.hatrack.nachtkrapp.match.PatternMatch.HABearishStrong;
import org.hatrack.nachtkrapp.match.PatternMatch.HABullishReversal;
import org.hatrack.nachtkrapp.match.PatternMatch.HABullishStrong;
import org.hatrack.nachtkrapp.match.PatternMatch.HADoji;
import org.hatrack.nachtkrapp.match.PatternMatch.MACDBearishCross;
import org.hatrack.nachtkrapp.match.PatternMatch.MACDBullishCross;
import org.hatrack.nachtkrapp.match.PatternMatch.MACDCrossedAboveZero;
import org.hatrack.nachtkrapp.match.PatternMatch.MACDCrossedBelowZero;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceAboveMA;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceBelowMA;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceAbovePivot;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceBelowPivot;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceCrossedAboveMA;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceCrossedAbovePivot;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceCrossedBelowMA;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceCrossedBelowPivot;
import org.hatrack.nachtkrapp.match.PatternMatch.RSICrossedAbove50;
import org.hatrack.nachtkrapp.match.PatternMatch.RSICrossedBelow50;
import org.hatrack.nachtkrapp.match.PatternMatch.RSIExitedOverbought;
import org.hatrack.nachtkrapp.match.PatternMatch.RSIExitedOversold;
import org.hatrack.nachtkrapp.match.PatternMatch.RSIOverbought;
import org.hatrack.nachtkrapp.match.PatternMatch.RSIOversold;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAColorChangeRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HADojiRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAStrongCandleRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDSignalCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDZeroCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PivotPointRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceMACrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceVsMARule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSILevel50CrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSIThresholdRule;
import org.hatrack.nachtkrapp.rule.MAType;
import org.hatrack.nachtkrapp.spec.DetectionSpec;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stateless detection logic. Applies each rule of a spec to its series and
 * produces matches per the canonical definitions in nachtkrapp/CLAUDE.md
 * sections 13-14. Lookahead-safe: a match at bar t depends only on bars &le; t.
 */
public final class DetectionEngine {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal FIFTY = new BigDecimal("50");

    private DetectionEngine() {
    }

    public static List<PatternMatch> run(DetectionSpec spec) {
        List<PatternMatch> matches = new ArrayList<>();
        Series series = spec.series();
        Optional<Timeframe> tf = spec.timeframe();
        for (DetectionRule rule : spec.rules()) {
            detect(rule, series, tf, matches);
        }
        matches.sort(Comparator.comparing(PatternMatch::time));
        return matches;
    }

    private static void detect(DetectionRule rule, Series series, Optional<Timeframe> tf,
                               List<PatternMatch> out) {
        switch (rule) {
            case HAColorChangeRule r -> haColorChange(r, series, tf, out);
            case HAStrongCandleRule r -> haStrongCandle(r, series, tf, out);
            case HADojiRule r -> haDoji(r, series, tf, out);
            case PriceVsMARule r -> priceVsMA(r, series, tf, out);
            case PriceMACrossRule r -> priceMACross(r, series, tf, out);
            case RSIThresholdRule r -> rsiThreshold(r, series, tf, out);
            case RSILevel50CrossRule r -> rsiLevel50Cross(r, series, tf, out);
            case MACDSignalCrossRule r -> macdSignalCross(r, series, tf, out);
            case MACDZeroCrossRule r -> macdZeroCross(r, series, tf, out);
            case PivotPointRule r -> pivotPoint(r, series, tf, out);
        }
    }

    // --- Heikin Ashi ---

    private static void haColorChange(HAColorChangeRule r, Series series, Optional<Timeframe> tf,
                                      List<PatternMatch> out) {
        List<HABar> bars = haBars(series);
        int m = r.minStreakLength();
        for (int t = m; t < bars.size(); t++) {
            boolean curBull = bullish(bars.get(t));
            boolean allBear = true;
            boolean allBull = true;
            for (int k = t - m; k <= t - 1; k++) {
                if (bullish(bars.get(k))) {
                    allBear = false;
                } else {
                    allBull = false;
                }
            }
            HABar bar = bars.get(t);
            if (allBear && curBull) {
                out.add(new HABullishReversal(bar.time(), tf, m, bar));
            } else if (allBull && !curBull) {
                out.add(new HABearishReversal(bar.time(), tf, m, bar));
            }
        }
    }

    private static void haStrongCandle(HAStrongCandleRule r, Series series, Optional<Timeframe> tf,
                                       List<PatternMatch> out) {
        for (HABar bar : haBars(series)) {
            BigDecimal range = bar.haHigh().subtract(bar.haLow(), MC);
            if (range.signum() == 0) {
                continue;
            }
            if (bullish(bar)) {
                BigDecimal bodyRatio = bar.haClose().subtract(bar.haOpen(), MC).divide(range, MC);
                BigDecimal lowerWick = bar.haOpen().subtract(bar.haLow(), MC).divide(range, MC);
                if (bodyRatio.compareTo(r.minBodyRatio()) >= 0
                        && lowerWick.compareTo(r.wickTolerance()) < 0) {
                    out.add(new HABullishStrong(bar.time(), tf, bodyRatio, lowerWick, bar));
                }
            } else {
                BigDecimal bodyRatio = bar.haOpen().subtract(bar.haClose(), MC).divide(range, MC);
                BigDecimal upperWick = bar.haHigh().subtract(bar.haOpen(), MC).divide(range, MC);
                if (bodyRatio.compareTo(r.minBodyRatio()) >= 0
                        && upperWick.compareTo(r.wickTolerance()) < 0) {
                    out.add(new HABearishStrong(bar.time(), tf, bodyRatio, upperWick, bar));
                }
            }
        }
    }

    private static void haDoji(HADojiRule r, Series series, Optional<Timeframe> tf,
                               List<PatternMatch> out) {
        for (HABar bar : haBars(series)) {
            BigDecimal range = bar.haHigh().subtract(bar.haLow(), MC);
            if (range.signum() == 0) {
                continue;
            }
            BigDecimal bodyRatio = bar.haClose().subtract(bar.haOpen(), MC).abs().divide(range, MC);
            if (bodyRatio.compareTo(r.maxBodyRatio()) <= 0) {
                out.add(new HADoji(bar.time(), tf, bodyRatio, bar));
            }
        }
    }

    // --- Moving Average ---

    private static void priceVsMA(PriceVsMARule r, Series series, Optional<Timeframe> tf,
                                  List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        BigDecimal[] ma = movingAverage(r.maType(), prices, r.period());
        for (int t = 0; t < prices.size(); t++) {
            if (ma[t] == null) {
                continue;
            }
            int cmp = prices.get(t).compareTo(ma[t]);
            if (cmp > 0) {
                out.add(new PriceAboveMA(times.get(t), tf, prices.get(t), ma[t], r.maType(), r.period()));
            } else if (cmp < 0) {
                out.add(new PriceBelowMA(times.get(t), tf, prices.get(t), ma[t], r.maType(), r.period()));
            }
        }
    }

    private static void priceMACross(PriceMACrossRule r, Series series, Optional<Timeframe> tf,
                                     List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        BigDecimal[] ma = movingAverage(r.maType(), prices, r.period());
        for (int t = 1; t < prices.size(); t++) {
            if (ma[t - 1] == null || ma[t] == null) {
                continue;
            }
            int prev = prices.get(t - 1).compareTo(ma[t - 1]);
            int cur = prices.get(t).compareTo(ma[t]);
            if (prev < 0 && cur >= 0) {
                out.add(new PriceCrossedAboveMA(times.get(t), tf, prices.get(t), ma[t], r.maType(), r.period()));
            } else if (prev > 0 && cur <= 0) {
                out.add(new PriceCrossedBelowMA(times.get(t), tf, prices.get(t), ma[t], r.maType(), r.period()));
            }
        }
    }

    // --- RSI ---

    private static void rsiThreshold(RSIThresholdRule r, Series series, Optional<Timeframe> tf,
                                     List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        BigDecimal[] rsi = Indicators.rsi(prices, r.period());
        for (int t = 0; t < rsi.length; t++) {
            if (rsi[t] == null) {
                continue;
            }
            if (rsi[t].compareTo(r.overbought()) > 0) {
                out.add(new RSIOverbought(times.get(t), tf, rsi[t], r.overbought(), r.period()));
            } else if (rsi[t].compareTo(r.oversold()) < 0) {
                out.add(new RSIOversold(times.get(t), tf, rsi[t], r.oversold(), r.period()));
            }
        }
        for (int t = 1; t < rsi.length; t++) {
            if (rsi[t - 1] == null || rsi[t] == null) {
                continue;
            }
            if (rsi[t - 1].compareTo(r.overbought()) > 0 && rsi[t].compareTo(r.overbought()) <= 0) {
                out.add(new RSIExitedOverbought(times.get(t), tf, rsi[t], r.overbought(), r.period()));
            }
            if (rsi[t - 1].compareTo(r.oversold()) < 0 && rsi[t].compareTo(r.oversold()) >= 0) {
                out.add(new RSIExitedOversold(times.get(t), tf, rsi[t], r.oversold(), r.period()));
            }
        }
    }

    private static void rsiLevel50Cross(RSILevel50CrossRule r, Series series, Optional<Timeframe> tf,
                                        List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        BigDecimal[] rsi = Indicators.rsi(prices, r.period());
        for (int t = 1; t < rsi.length; t++) {
            if (rsi[t - 1] == null || rsi[t] == null) {
                continue;
            }
            int prev = rsi[t - 1].compareTo(FIFTY);
            int cur = rsi[t].compareTo(FIFTY);
            if (prev < 0 && cur >= 0) {
                out.add(new RSICrossedAbove50(times.get(t), tf, rsi[t], r.period()));
            } else if (prev > 0 && cur <= 0) {
                out.add(new RSICrossedBelow50(times.get(t), tf, rsi[t], r.period()));
            }
        }
    }

    // --- MACD ---

    private static void macdSignalCross(MACDSignalCrossRule r, Series series, Optional<Timeframe> tf,
                                        List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        MacdResult macd = Indicators.macd(prices, r.fastPeriod(), r.slowPeriod(), r.signalPeriod());
        BigDecimal[] hist = macd.histogram();
        for (int t = 1; t < hist.length; t++) {
            if (hist[t - 1] == null || hist[t] == null) {
                continue;
            }
            if (hist[t - 1].signum() < 0 && hist[t].signum() >= 0) {
                out.add(new MACDBullishCross(times.get(t), tf, macd.macdLine()[t], macd.signalLine()[t],
                        r.fastPeriod(), r.slowPeriod(), r.signalPeriod()));
            } else if (hist[t - 1].signum() > 0 && hist[t].signum() <= 0) {
                out.add(new MACDBearishCross(times.get(t), tf, macd.macdLine()[t], macd.signalLine()[t],
                        r.fastPeriod(), r.slowPeriod(), r.signalPeriod()));
            }
        }
    }

    private static void macdZeroCross(MACDZeroCrossRule r, Series series, Optional<Timeframe> tf,
                                      List<PatternMatch> out) {
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        MacdResult macd = Indicators.macd(prices, r.fastPeriod(), r.slowPeriod(), r.signalPeriod());
        BigDecimal[] line = macd.macdLine();
        for (int t = 1; t < line.length; t++) {
            if (line[t - 1] == null || line[t] == null) {
                continue;
            }
            if (line[t - 1].signum() < 0 && line[t].signum() >= 0) {
                out.add(new MACDCrossedAboveZero(times.get(t), tf, line[t],
                        r.fastPeriod(), r.slowPeriod(), r.signalPeriod()));
            } else if (line[t - 1].signum() > 0 && line[t].signum() <= 0) {
                out.add(new MACDCrossedBelowZero(times.get(t), tf, line[t],
                        r.fastPeriod(), r.slowPeriod(), r.signalPeriod()));
            }
        }
    }

    // --- Pivot points ---

    private static void pivotPoint(PivotPointRule r, Series series, Optional<Timeframe> tf,
                                   List<PatternMatch> out) {
        // V5 guarantees an OHLC priceSource, hence an OHLCSeries.
        OHLCSeries ohlc = (OHLCSeries) series;
        List<BigDecimal> prices = prices(series, r.priceSource());
        List<Instant> times = times(series);
        List<OHLCBar> periodBars = OHLCAggregator.toPeriod(ohlc, r.pivotPeriod()).bars();

        int n = prices.size();
        PivotLevels[] levelsAt = new PivotLevels[n];
        for (int t = 0; t < n; t++) {
            OHLCBar prior = priorClosedPeriod(periodBars, times.get(t), r.pivotPeriod());
            levelsAt[t] = prior == null ? null : PivotPoints.levels(prior, r.variant());
        }

        for (PivotLevel level : PivotLevel.values()) {
            for (int t = 0; t < n; t++) {
                if (levelsAt[t] == null) {
                    continue;
                }
                BigDecimal lv = levelsAt[t].value(level);
                if (lv == null) {
                    continue;
                }
                int cmp = prices.get(t).compareTo(lv);
                if (cmp > 0) {
                    out.add(new PriceAbovePivot(times.get(t), tf, prices.get(t), lv, level,
                            r.variant(), r.pivotPeriod()));
                } else if (cmp < 0) {
                    out.add(new PriceBelowPivot(times.get(t), tf, prices.get(t), lv, level,
                            r.variant(), r.pivotPeriod()));
                }
            }
            for (int t = 1; t < n; t++) {
                if (levelsAt[t - 1] == null || levelsAt[t] == null) {
                    continue;
                }
                BigDecimal prevLv = levelsAt[t - 1].value(level);
                BigDecimal curLv = levelsAt[t].value(level);
                if (prevLv == null || curLv == null) {
                    continue;
                }
                int prev = prices.get(t - 1).compareTo(prevLv);
                int cur = prices.get(t).compareTo(curLv);
                if (prev < 0 && cur >= 0) {
                    out.add(new PriceCrossedAbovePivot(times.get(t), tf, prices.get(t), curLv, level,
                            r.variant(), r.pivotPeriod()));
                } else if (prev > 0 && cur <= 0) {
                    out.add(new PriceCrossedBelowPivot(times.get(t), tf, prices.get(t), curLv, level,
                            r.variant(), r.pivotPeriod()));
                }
            }
        }
    }

    /**
     * The most-recent CLOSED aggregated period at or before {@code barTime} — the
     * latest period whose end (start + one period length) is {@code <= barTime}.
     * Returns {@code null} for bars in the first period (no prior closed period).
     */
    private static OHLCBar priorClosedPeriod(List<OHLCBar> periodBars, Instant barTime,
                                             Timeframe period) {
        OHLCBar prior = null;
        for (OHLCBar pb : periodBars) {
            Instant close = periodEnd(pb.time(), period);
            if (!close.isAfter(barTime)) {
                prior = pb;
            } else {
                break;
            }
        }
        return prior;
    }

    private static Instant periodEnd(Instant start, Timeframe period) {
        return switch (period.unit()) {
            case DAY -> start.plus(1, ChronoUnit.DAYS);
            case WEEK -> start.plus(7, ChronoUnit.DAYS);
            default -> throw new IllegalStateException("unsupported pivot period: " + period.wire());
        };
    }

    // --- helpers ---

    private static boolean bullish(HABar bar) {
        return bar.haClose().compareTo(bar.haOpen()) >= 0;
    }

    private static List<HABar> haBars(Series series) {
        return ((HASeries) series).bars();
    }

    private static BigDecimal[] movingAverage(MAType type, List<BigDecimal> src, int period) {
        return type == MAType.SMA ? Indicators.sma(src, period) : Indicators.ema(src, period);
    }

    private static List<Instant> times(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::time).toList();
            case HASeries h -> h.bars().stream().map(HABar::time).toList();
        };
    }

    private static List<BigDecimal> prices(Series series, PriceSource source) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(b -> ohlcPrice(b, source)).toList();
            case HASeries h -> h.bars().stream().map(b -> haPrice(b, source)).toList();
        };
    }

    private static BigDecimal ohlcPrice(OHLCBar bar, PriceSource source) {
        return switch (source) {
            case OPEN -> bar.open();
            case HIGH -> bar.high();
            case LOW -> bar.low();
            case CLOSE -> bar.close();
            case HA_OPEN, HA_HIGH, HA_LOW, HA_CLOSE ->
                    throw new IllegalStateException("HA price source on OHLC series: " + source);
        };
    }

    private static BigDecimal haPrice(HABar bar, PriceSource source) {
        return switch (source) {
            case HA_OPEN -> bar.haOpen();
            case HA_HIGH -> bar.haHigh();
            case HA_LOW -> bar.haLow();
            case HA_CLOSE -> bar.haClose();
            case OPEN, HIGH, LOW, CLOSE ->
                    throw new IllegalStateException("OHLC price source on HA series: " + source);
        };
    }
}
