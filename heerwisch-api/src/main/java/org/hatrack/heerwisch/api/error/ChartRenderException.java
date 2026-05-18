package org.hatrack.heerwisch.api.error;

/**
 * Root of the checked exception hierarchy for the plotting library. Abstract;
 * never thrown directly.
 */
public abstract sealed class ChartRenderException extends Exception
        permits InvalidChartSpecException, UnsupportedFeatureException,
        InsufficientDataException, DriverInternalException {

    protected ChartRenderException(String message) {
        super(message);
    }

    protected ChartRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
