package org.hatrack.frauholle.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Closed hierarchy of strategy signals. Variants are nested records.
 */
public sealed interface Signal {

    record Hold() implements Signal {
    }

    record Buy(BigDecimal quantity) implements Signal {
        public Buy {
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("Buy quantity must be > 0, was " + quantity);
            }
        }
    }

    record Sell(BigDecimal quantity) implements Signal {
        public Sell {
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("Sell quantity must be > 0, was " + quantity);
            }
        }
    }

    record ClosePosition() implements Signal {
    }

    /**
     * v1.1 additive variant: closes any open position at an explicit intrabar
     * price and time, rather than at the next bar open. {@code fillTime} must
     * not reach beyond the bar after the bar at which the signal was emitted
     * (lookahead-safety, enforced by the backtester).
     */
    record ClosePositionAtPrice(BigDecimal price, Instant fillTime) implements Signal {
        public ClosePositionAtPrice {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(fillTime, "fillTime");
            if (price.signum() <= 0) {
                throw new IllegalArgumentException(
                        "ClosePositionAtPrice price must be > 0, was " + price);
            }
        }
    }
}
