package org.hatrack.nachtkrapp.detector;

import org.hatrack.nachtkrapp.error.DetectionException;
import org.hatrack.nachtkrapp.error.DetectionInternalException;
import org.hatrack.nachtkrapp.internal.DetectionEngine;
import org.hatrack.nachtkrapp.match.PatternMatch;
import org.hatrack.nachtkrapp.spec.DetectionSpec;

import java.util.List;
import java.util.Objects;

/**
 * The concrete rule-based {@link PatternDetector}. Stateless, therefore safe
 * to share across threads.
 */
public final class RuleBasedPatternDetector implements PatternDetector {

    @Override
    public DetectionResult detect(DetectionSpec spec) throws DetectionException {
        Objects.requireNonNull(spec, "spec");
        try {
            List<PatternMatch> matches = DetectionEngine.run(spec);
            return new DetectionResult(matches);
        } catch (RuntimeException e) {
            throw new DetectionInternalException(e);
        }
    }
}
