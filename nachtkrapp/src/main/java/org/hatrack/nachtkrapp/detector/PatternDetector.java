package org.hatrack.nachtkrapp.detector;

import org.hatrack.nachtkrapp.error.DetectionException;
import org.hatrack.nachtkrapp.spec.DetectionSpec;

/**
 * Entry point of the pattern detection library. Implementations MUST be
 * thread-safe, stateless or backed only by thread-safe state, deterministic,
 * and lookahead-safe.
 */
public interface PatternDetector {

    DetectionResult detect(DetectionSpec spec) throws DetectionException;
}
