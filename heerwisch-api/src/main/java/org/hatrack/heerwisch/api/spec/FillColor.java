package org.hatrack.heerwisch.api.spec;

/**
 * Semantic fill role for an {@link Annotation.TimeRangeHighlight}. The
 * renderer maps each value to a base color from its theme; the per-instance
 * opacity controls the band's transparency.
 *
 * <p>Two intents are served:
 *
 * <ul>
 *   <li><b>Direction-oriented</b> — shade trade duration by side.</li>
 *   <li><b>Outcome-oriented</b> — shade trade duration by result
 *       (TradingView and similar tools' convention).</li>
 * </ul>
 *
 * <p>Direction-oriented variants:
 *
 * <ul>
 *   <li>{@link #LONG_POSITION} — light green (an open long position).</li>
 *   <li>{@link #SHORT_POSITION} — light red (an open short position).</li>
 *   <li>{@link #NEUTRAL} — light blue/grey (informational band).</li>
 *   <li>{@link #CAUTION} — light yellow/amber (warning band).</li>
 * </ul>
 *
 * <p>Outcome-oriented variants:
 *
 * <ul>
 *   <li>{@link #WIN} — light green (winning trade).</li>
 *   <li>{@link #LOSS} — light red (losing trade).</li>
 *   <li>{@link #OPEN} — light grey (still-open trade at backtest end).</li>
 * </ul>
 *
 * <p>The constant names are contractual: consumers should pick the variant
 * whose semantic intent matches their use case (direction vs outcome).
 * Today {@code WIN} renders in the same green as {@code LONG_POSITION} and
 * {@code LOSS} in the same red as {@code SHORT_POSITION}; the renderer may
 * differentiate the tones in future without API change.
 */
public enum FillColor {
    LONG_POSITION,
    SHORT_POSITION,
    NEUTRAL,
    CAUTION,
    WIN,
    LOSS,
    OPEN
}
