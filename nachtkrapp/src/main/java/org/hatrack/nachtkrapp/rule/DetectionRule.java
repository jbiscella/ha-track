package org.hatrack.nachtkrapp.rule;

import org.hatrack.commons.PriceSource;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Closed hierarchy of detection rules. Variants are nested records.
 *
 * <p>Canonical constructors perform null-checks only. Parameter range and
 * relationship constraints are validated eagerly by the spec builder (rule V7),
 * so an out-of-range rule can still be constructed and is rejected at build().
 */
public sealed interface DetectionRule {

    /** Minimum number of bars required for this rule to produce its first match. */
    int minBars();

    record HAColorChangeRule(int minStreakLength) implements DetectionRule {
        @Override
        public int minBars() {
            return minStreakLength + 1;
        }
    }

    record HAStrongCandleRule(BigDecimal wickTolerance, BigDecimal minBodyRatio) implements DetectionRule {
        public HAStrongCandleRule {
            Objects.requireNonNull(wickTolerance, "wickTolerance");
            Objects.requireNonNull(minBodyRatio, "minBodyRatio");
        }

        @Override
        public int minBars() {
            return 1;
        }
    }

    record HADojiRule(BigDecimal maxBodyRatio) implements DetectionRule {
        public HADojiRule {
            Objects.requireNonNull(maxBodyRatio, "maxBodyRatio");
        }

        @Override
        public int minBars() {
            return 1;
        }
    }

    record PriceVsMARule(MAType maType, int period, PriceSource priceSource) implements DetectionRule {
        public PriceVsMARule {
            Objects.requireNonNull(maType, "maType");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }
    }

    record PriceMACrossRule(MAType maType, int period, PriceSource priceSource) implements DetectionRule {
        public PriceMACrossRule {
            Objects.requireNonNull(maType, "maType");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period + 1;
        }
    }

    record MAVsMARule(MAType aType, int aPeriod, MAType bType, int bPeriod, PriceSource priceSource)
            implements DetectionRule {
        public MAVsMARule {
            Objects.requireNonNull(aType, "aType");
            Objects.requireNonNull(bType, "bType");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return Math.max(aPeriod, bPeriod);
        }
    }

    record MACrossMARule(MAType aType, int aPeriod, MAType bType, int bPeriod, PriceSource priceSource)
            implements DetectionRule {
        public MACrossMARule {
            Objects.requireNonNull(aType, "aType");
            Objects.requireNonNull(bType, "bType");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return Math.max(aPeriod, bPeriod) + 1;
        }
    }

    record RSIThresholdRule(int period, BigDecimal overbought, BigDecimal oversold, PriceSource priceSource)
            implements DetectionRule {
        public RSIThresholdRule {
            Objects.requireNonNull(overbought, "overbought");
            Objects.requireNonNull(oversold, "oversold");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period + 1;
        }
    }

    record RSILevel50CrossRule(int period, PriceSource priceSource) implements DetectionRule {
        public RSILevel50CrossRule {
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period + 2;
        }
    }

    record MACDSignalCrossRule(int fastPeriod, int slowPeriod, int signalPeriod, PriceSource priceSource)
            implements DetectionRule {
        public MACDSignalCrossRule {
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return slowPeriod + signalPeriod + 1;
        }
    }

    record MACDZeroCrossRule(int fastPeriod, int slowPeriod, int signalPeriod, PriceSource priceSource)
            implements DetectionRule {
        public MACDZeroCrossRule {
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return slowPeriod + 1;
        }
    }
}
