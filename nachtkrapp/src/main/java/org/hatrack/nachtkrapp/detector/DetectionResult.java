package org.hatrack.nachtkrapp.detector;

import org.hatrack.nachtkrapp.match.PatternMatch;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a detection run. {@code matches()} is ordered ascending
 * by {@code time()}.
 */
public record DetectionResult(List<PatternMatch> matches) {

    public DetectionResult {
        Objects.requireNonNull(matches, "matches");
        matches = List.copyOf(matches);
    }
}
