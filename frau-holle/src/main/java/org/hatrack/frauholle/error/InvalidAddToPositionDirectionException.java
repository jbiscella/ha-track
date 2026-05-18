package org.hatrack.frauholle.error;

import org.hatrack.frauholle.model.Direction;

import java.time.Instant;

/**
 * v1.2: thrown by the backtester when an {@code AddToPosition} signal carries a
 * {@code direction} opposite to the direction of the currently open position.
 * Pyramiding can only enlarge an existing position; reversing direction via
 * {@code AddToPosition} has ambiguous semantics and is rejected in v1.2.
 */
public final class InvalidAddToPositionDirectionException extends BacktestException {

    private final int barIndex;
    private final transient Instant barTime;
    private final transient Direction openPositionDirection;
    private final transient Direction signalDirection;

    public InvalidAddToPositionDirectionException(int barIndex, Instant barTime,
                                                  Direction openPositionDirection,
                                                  Direction signalDirection) {
        super("AddToPosition at bar " + barIndex + " (" + barTime + ") has direction "
                + signalDirection + " but the open position is " + openPositionDirection);
        this.barIndex = barIndex;
        this.barTime = barTime;
        this.openPositionDirection = openPositionDirection;
        this.signalDirection = signalDirection;
    }

    /** Index of the fill bar at which the direction mismatch was detected. */
    public int barIndex() {
        return barIndex;
    }

    /** Time of the fill bar at which the direction mismatch was detected. */
    public Instant barTime() {
        return barTime;
    }

    /** Direction of the position that is currently open. */
    public Direction openPositionDirection() {
        return openPositionDirection;
    }

    /** Direction carried by the offending {@code AddToPosition} signal. */
    public Direction signalDirection() {
        return signalDirection;
    }
}
