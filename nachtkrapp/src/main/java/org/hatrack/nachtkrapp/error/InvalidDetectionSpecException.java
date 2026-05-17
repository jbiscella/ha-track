package org.hatrack.nachtkrapp.error;

/**
 * Thrown by {@code DetectionSpecBuilder.build()} when the spec is malformed
 * (any V1-V9 rule violation).
 */
public final class InvalidDetectionSpecException extends DetectionException {

    private final String violatedRule;
    private final transient Object offendingValue;

    public InvalidDetectionSpecException(String violatedRule, Object offendingValue) {
        super("invalid detection spec [" + violatedRule + "]"
                + (offendingValue == null ? "" : ": " + offendingValue));
        this.violatedRule = violatedRule;
        this.offendingValue = offendingValue;
    }

    public String violatedRule() {
        return violatedRule;
    }

    public Object offendingValue() {
        return offendingValue;
    }
}
