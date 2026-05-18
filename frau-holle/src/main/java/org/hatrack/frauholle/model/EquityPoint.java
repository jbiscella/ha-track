package org.hatrack.frauholle.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One point of the equity curve: total equity, cash and open-position value at a bar. */
public record EquityPoint(Instant time, BigDecimal equity, BigDecimal cash,
                          BigDecimal positionValue) {

    public EquityPoint {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(equity, "equity");
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(positionValue, "positionValue");
    }
}
