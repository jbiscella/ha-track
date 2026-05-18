package org.hatrack.frauholle.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** An open position. */
public record Position(Direction direction, BigDecimal quantity, Instant entryTime,
                       BigDecimal entryPrice) {

    public Position {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(entryTime, "entryTime");
        Objects.requireNonNull(entryPrice, "entryPrice");
    }
}
