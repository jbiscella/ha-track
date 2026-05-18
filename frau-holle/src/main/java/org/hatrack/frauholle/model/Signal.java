package org.hatrack.frauholle.model;

import java.math.BigDecimal;
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
}
