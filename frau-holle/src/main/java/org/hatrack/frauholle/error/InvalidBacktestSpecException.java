package org.hatrack.frauholle.error;

/** Thrown by {@code BacktestSpecBuilder.build()} when the spec is malformed (V1-V6). */
public final class InvalidBacktestSpecException extends BacktestException {

    private final String violatedRule;
    private final transient Object offendingValue;

    public InvalidBacktestSpecException(String violatedRule, Object offendingValue) {
        super("invalid backtest spec [" + violatedRule + "]"
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
