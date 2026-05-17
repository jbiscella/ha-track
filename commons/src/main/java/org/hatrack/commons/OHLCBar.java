package org.hatrack.commons;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record OHLCBar(
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Optional<BigDecimal> volume) {

    public OHLCBar {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }

    public void validateInvariants() {
        BigDecimal zero = BigDecimal.ZERO;
        if (open.compareTo(zero) <= 0) {
            throw violation("open");
        }
        if (high.compareTo(zero) <= 0) {
            throw violation("high");
        }
        if (low.compareTo(zero) <= 0) {
            throw violation("low");
        }
        if (close.compareTo(zero) <= 0) {
            throw violation("close");
        }
        if (high.compareTo(low) < 0) {
            throw violation("high<low");
        }
        if (high.compareTo(open) < 0) {
            throw violation("high<open");
        }
        if (high.compareTo(close) < 0) {
            throw violation("high<close");
        }
        if (low.compareTo(open) > 0) {
            throw violation("low>open");
        }
        if (low.compareTo(close) > 0) {
            throw violation("low>close");
        }
        if (volume.isPresent() && volume.get().compareTo(zero) < 0) {
            throw violation("volume");
        }
    }

    private OHLCInvariantViolationException violation(String invariant) {
        return new OHLCInvariantViolationException(time, invariant);
    }
}
