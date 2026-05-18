package org.hatrack.heerwisch.api.error;

/**
 * Thrown by {@code ChartSpecBuilder.build()} when the spec is malformed (any
 * V1-V11 rule violation).
 */
public final class InvalidChartSpecException extends ChartRenderException {

    private final String violatedRule;
    private final transient Object offendingValue;

    public InvalidChartSpecException(String violatedRule, Object offendingValue) {
        super("invalid chart spec [" + violatedRule + "]"
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
