package org.hatrack.commons;

import java.util.List;
import java.util.Objects;

public record HASeries(List<HABar> bars) implements Series {

    public HASeries {
        Objects.requireNonNull(bars, "bars");
        bars = List.copyOf(bars);
    }
}
