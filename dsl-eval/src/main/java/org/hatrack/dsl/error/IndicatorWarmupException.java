package org.hatrack.dsl.error;

/**
 * Raised when an indicator (or window aggregate) cannot be evaluated at the
 * current bar because not enough history has accumulated yet — the indicator's
 * warm-up window.
 *
 * <p>This is not a hard error: it is the expected state for the early bars of
 * any evaluation. The caller catches it and treats the affected condition as
 * not met, so iteration proceeds rather than aborting. Genuine evaluation
 * errors use {@link DslEvaluationException} instead.
 *
 * <p>Unchecked because it is raised from deep inside per-bar evaluation and is
 * caught at the per-bar boundary by the caller.
 */
public final class IndicatorWarmupException extends RuntimeException {

    public IndicatorWarmupException(String message) {
        super(message);
    }
}
