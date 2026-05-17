package org.hatrack.commons;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record HABar(
        Instant time,
        BigDecimal haOpen,
        BigDecimal haHigh,
        BigDecimal haLow,
        BigDecimal haClose) {

    public HABar {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(haOpen, "haOpen");
        Objects.requireNonNull(haHigh, "haHigh");
        Objects.requireNonNull(haLow, "haLow");
        Objects.requireNonNull(haClose, "haClose");
    }
}
