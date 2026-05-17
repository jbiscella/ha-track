package org.hatrack.nachtkrapp.error;

/**
 * Root of the checked exception hierarchy for {@code nachtkrapp}. Abstract;
 * never thrown directly.
 */
public abstract sealed class DetectionException extends Exception
        permits InvalidDetectionSpecException, InsufficientDataException, DetectionInternalException {

    protected DetectionException(String message) {
        super(message);
    }

    protected DetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
