package org.hatrack.frauholle.error;

/** Thrown when the {@code SignalGenerator} itself fails at a given bar. */
public final class SignalGenerationException extends BacktestException {

    private final int barIndex;

    public SignalGenerationException(int barIndex, Throwable cause) {
        super("signal generation failed at bar " + barIndex, cause);
        this.barIndex = barIndex;
    }

    public int barIndex() {
        return barIndex;
    }
}
