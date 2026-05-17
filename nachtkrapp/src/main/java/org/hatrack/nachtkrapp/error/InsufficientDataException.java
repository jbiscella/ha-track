package org.hatrack.nachtkrapp.error;

/**
 * Thrown by {@code PatternDetector.detect()} when the data is insufficient for
 * a rule at runtime. This is an escape hatch; the condition is preferably
 * caught at build time by rule V6.
 */
public final class InsufficientDataException extends DetectionException {

    private final String ruleClassName;
    private final int requiredBars;
    private final int availableBars;

    public InsufficientDataException(String ruleClassName, int requiredBars, int availableBars) {
        super("insufficient data for " + ruleClassName
                + ": requires " + requiredBars + " bars, " + availableBars + " available");
        this.ruleClassName = ruleClassName;
        this.requiredBars = requiredBars;
        this.availableBars = availableBars;
    }

    public String ruleClassName() {
        return ruleClassName;
    }

    public int requiredBars() {
        return requiredBars;
    }

    public int availableBars() {
        return availableBars;
    }
}
