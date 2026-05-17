package org.hatrack.commons;

import java.time.Instant;
import java.util.Objects;

public final class OHLCInvariantViolationException extends RuntimeException {

    private final Instant time;
    private final String violatedInvariant;

    public OHLCInvariantViolationException(Instant time, String violatedInvariant) {
        super("OHLC invariant violated [" + violatedInvariant + "] at " + time);
        this.time = Objects.requireNonNull(time, "time");
        this.violatedInvariant = Objects.requireNonNull(violatedInvariant, "violatedInvariant");
    }

    public Instant time() {
        return time;
    }

    public String violatedInvariant() {
        return violatedInvariant;
    }
}
