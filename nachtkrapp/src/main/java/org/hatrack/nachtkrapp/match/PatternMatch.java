package org.hatrack.nachtkrapp.match;

import org.hatrack.commons.HABar;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.rule.MAType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Closed hierarchy of pattern matches. Every variant carries the common
 * accessors {@code time()}, {@code flavor()} and {@code timeframe()} plus a
 * variant-specific diagnostic payload. Variants are nested records.
 */
public sealed interface PatternMatch {

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
}
