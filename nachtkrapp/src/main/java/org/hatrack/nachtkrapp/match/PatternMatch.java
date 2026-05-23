package org.hatrack.nachtkrapp.match;

import org.hatrack.commons.HABar;
import org.hatrack.commons.PivotLevel;
import org.hatrack.commons.PivotPointVariant;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.rule.MAType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Closed hierarchy of pattern matches. Every variant carries the common
 * accessors {@code time()}, {@code flavor()} and {@code timeframe()} plus a
 * variant-specific diagnostic payload. Variants are nested records.
 *
 * <p>The {@code permits} clause is stated explicitly. It is semantically
 * identical to the implicit clause the compiler infers from the nested
 * records, but makes the closed set visible at the declaration and in the
 * generated javadoc. Consumers may rely on it for exhaustive {@code switch}.
 */
public sealed interface PatternMatch
        permits PatternMatch.HABullishReversal,
                PatternMatch.HABearishReversal,
                PatternMatch.HABullishStrong,
                PatternMatch.HABearishStrong,
                PatternMatch.HADoji,
                PatternMatch.PriceAboveMA,
                PatternMatch.PriceBelowMA,
                PatternMatch.PriceCrossedAboveMA,
                PatternMatch.PriceCrossedBelowMA,
                PatternMatch.RSIOverbought,
                PatternMatch.RSIOversold,
                PatternMatch.RSIExitedOverbought,
                PatternMatch.RSIExitedOversold,
                PatternMatch.RSICrossedAbove50,
                PatternMatch.RSICrossedBelow50,
                PatternMatch.MACDBullishCross,
                PatternMatch.MACDBearishCross,
                PatternMatch.MACDCrossedAboveZero,
                PatternMatch.MACDCrossedBelowZero,
                PatternMatch.PriceAbovePivot,
                PatternMatch.PriceBelowPivot,
                PatternMatch.PriceCrossedAbovePivot,
                PatternMatch.PriceCrossedBelowPivot {

    /** Time of the bar that triggered the match. */
    Instant time();

    /** EVENT for discrete transitions, STATE for continuous conditions. */
    MatchFlavor flavor();

    /** Timeframe tag propagated from the DetectionSpec; empty if not specified. */
    Optional<Timeframe> timeframe();

    // --- Heikin Ashi ---

    record HABullishReversal(Instant time, Optional<Timeframe> timeframe, int streakLength, HABar bar)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record HABearishReversal(Instant time, Optional<Timeframe> timeframe, int streakLength, HABar bar)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record HABullishStrong(Instant time, Optional<Timeframe> timeframe,
                           BigDecimal bodyRatio, BigDecimal lowerWickRatio, HABar bar)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record HABearishStrong(Instant time, Optional<Timeframe> timeframe,
                           BigDecimal bodyRatio, BigDecimal upperWickRatio, HABar bar)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record HADoji(Instant time, Optional<Timeframe> timeframe, BigDecimal bodyRatio, HABar bar)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    // --- Moving Average ---

    record PriceAboveMA(Instant time, Optional<Timeframe> timeframe,
                        BigDecimal price, BigDecimal maValue, MAType maType, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record PriceBelowMA(Instant time, Optional<Timeframe> timeframe,
                        BigDecimal price, BigDecimal maValue, MAType maType, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record PriceCrossedAboveMA(Instant time, Optional<Timeframe> timeframe,
                               BigDecimal price, BigDecimal maValue, MAType maType, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record PriceCrossedBelowMA(Instant time, Optional<Timeframe> timeframe,
                               BigDecimal price, BigDecimal maValue, MAType maType, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    // --- RSI ---

    record RSIOverbought(Instant time, Optional<Timeframe> timeframe,
                         BigDecimal rsiValue, BigDecimal threshold, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record RSIOversold(Instant time, Optional<Timeframe> timeframe,
                       BigDecimal rsiValue, BigDecimal threshold, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record RSIExitedOverbought(Instant time, Optional<Timeframe> timeframe,
                               BigDecimal rsiValue, BigDecimal threshold, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record RSIExitedOversold(Instant time, Optional<Timeframe> timeframe,
                             BigDecimal rsiValue, BigDecimal threshold, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record RSICrossedAbove50(Instant time, Optional<Timeframe> timeframe, BigDecimal rsiValue, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record RSICrossedBelow50(Instant time, Optional<Timeframe> timeframe, BigDecimal rsiValue, int period)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    // --- MACD ---

    record MACDBullishCross(Instant time, Optional<Timeframe> timeframe,
                            BigDecimal macdValue, BigDecimal signalValue,
                            int fastPeriod, int slowPeriod, int signalPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record MACDBearishCross(Instant time, Optional<Timeframe> timeframe,
                            BigDecimal macdValue, BigDecimal signalValue,
                            int fastPeriod, int slowPeriod, int signalPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record MACDCrossedAboveZero(Instant time, Optional<Timeframe> timeframe,
                                BigDecimal macdValue, int fastPeriod, int slowPeriod, int signalPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record MACDCrossedBelowZero(Instant time, Optional<Timeframe> timeframe,
                                BigDecimal macdValue, int fastPeriod, int slowPeriod, int signalPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    // --- Pivot points ---

    record PriceAbovePivot(Instant time, Optional<Timeframe> timeframe,
                           BigDecimal price, BigDecimal levelValue, PivotLevel level,
                           PivotPointVariant variant, Timeframe pivotPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record PriceBelowPivot(Instant time, Optional<Timeframe> timeframe,
                           BigDecimal price, BigDecimal levelValue, PivotLevel level,
                           PivotPointVariant variant, Timeframe pivotPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.STATE;
        }
    }

    record PriceCrossedAbovePivot(Instant time, Optional<Timeframe> timeframe,
                                  BigDecimal price, BigDecimal levelValue, PivotLevel level,
                                  PivotPointVariant variant, Timeframe pivotPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }

    record PriceCrossedBelowPivot(Instant time, Optional<Timeframe> timeframe,
                                  BigDecimal price, BigDecimal levelValue, PivotLevel level,
                                  PivotPointVariant variant, Timeframe pivotPeriod)
            implements PatternMatch {
        @Override
        public MatchFlavor flavor() {
            return MatchFlavor.EVENT;
        }
    }
}
