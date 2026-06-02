package org.hatrack.dsl.error;

import java.time.Instant;

/**
 * Thrown when a runtime error occurs during DSL expression evaluation, such as
 * division by zero, a malformed expression, or an unsupported indicator call.
 *
 * <p>Unchecked by design: the evaluator runs inside a recursive-descent parse
 * over a single expression and raises this from arbitrary depth. Keeping it
 * unchecked preserves the evaluator's signatures (and therefore the byte-for-byte
 * evaluation behaviour shared across consumers) without threading {@code throws}
 * clauses through the descent.
 *
 * <p>The diagnostic context ({@code source}, line, bar time, bar index, and the
 * offending expression text) is carried verbatim so a consumer can render a
 * precise message; the fields are pure data and the consumer supplies whatever
 * locator strings make sense for it.
 */
public final class DslEvaluationException extends RuntimeException {

    private final String source;
    private final int lineNumber;
    private final Instant barTime;
    private final long barIndex;
    private final String expressionText;

    public DslEvaluationException(String source, int lineNumber, Instant barTime,
                                  long barIndex, String expressionText, String message,
                                  Throwable cause) {
        super(message, cause);
        this.source = source;
        this.lineNumber = lineNumber;
        this.barTime = barTime;
        this.barIndex = barIndex;
        this.expressionText = expressionText;
    }

    public DslEvaluationException(String source, int lineNumber, Instant barTime,
                                  long barIndex, String expressionText, String message) {
        this(source, lineNumber, barTime, barIndex, expressionText, message, null);
    }

    /** Consumer-supplied locator for the evaluated unit (e.g. a strategy or file name). */
    public String source() {
        return source;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public Instant barTime() {
        return barTime;
    }

    public long barIndex() {
        return barIndex;
    }

    public String expressionText() {
        return expressionText;
    }
}
