package org.hatrack.commons;

import java.util.List;
import java.util.Objects;

public record OHLCSeries(List<OHLCBar> bars) implements Series {

    public OHLCSeries {
        Objects.requireNonNull(bars, "bars");
        bars = List.copyOf(bars);
    }
}
