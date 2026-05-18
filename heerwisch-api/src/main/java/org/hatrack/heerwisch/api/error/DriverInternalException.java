package org.hatrack.heerwisch.api.error;

/**
 * Thrown by {@code ChartRenderer.render()} when the underlying driver fails
 * internally. The cause is mandatory.
 */
public final class DriverInternalException extends ChartRenderException {

    public DriverInternalException(Throwable cause) {
        super("driver internal error", cause);
    }
}
