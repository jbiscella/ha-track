package org.hatrack.frauholle.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A completed (entered and closed) position. */
public record Trade(Direction direction, BigDecimal quantity, Instant entryTime,
                     BigDecimal entryPrice, Instant exitTime, BigDecimal exitPrice,
                     BigDecimal pnl, BigDecimal pnlPercent) {

    public Trade {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(entryTime, "entryTime");
        Objects.requireNonNull(entryPrice, "entryPrice");
        Objects.requireNonNull(exitTime, "exitTime");
        Objects.requireNonNull(exitPrice, "exitPrice");
        Objects.requireNonNull(pnl, "pnl");
        Objects.requireNonNull(pnlPercent, "pnlPercent");
    }
}
