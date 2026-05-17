package org.hatrack.nachtkrapp.error;

/**
 * Thrown by {@code PatternDetector.detect()} when an internal error occurs
 * inside the detection logic. The cause is mandatory.
 */
public final class DetectionInternalException extends DetectionException {

    public DetectionInternalException(Throwable cause) {
        super("internal detection error", cause);
    }
}
