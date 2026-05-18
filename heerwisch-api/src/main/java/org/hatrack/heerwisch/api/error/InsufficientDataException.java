package org.hatrack.heerwisch.api.error;

/**
 * Thrown by {@code ChartRenderer.render()} when the data is insufficient for
 * an indicator at render time. Preferably caught at build via V6.
 */
public final class InsufficientDataException extends ChartRenderException {

    private final String indicatorName;
    private final int requiredBars;
    private final int availableBars;

    public InsufficientDataException(String indicatorName, int requiredBars, int availableBars) {
        super("insufficient data for " + indicatorName
                + ": requires " + requiredBars + " bars, " + availableBars + " available");
        this.indicatorName = indicatorName;
        this.requiredBars = requiredBars;
        this.availableBars = availableBars;
    }

    public String indicatorName() {
        return indicatorName;
    }

    public int requiredBars() {
        return requiredBars;
    }

    public int availableBars() {
        return availableBars;
    }
}
