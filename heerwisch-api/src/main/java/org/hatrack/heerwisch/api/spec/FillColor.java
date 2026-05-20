package org.hatrack.heerwisch.api.spec;

/**
 * Semantic fill role for an {@link Annotation.TimeRangeHighlight}. The
 * renderer maps each value to a base color from its theme; the per-instance
 * opacity controls the band's transparency.
 *
 * <ul>
 *   <li>{@code LONG_POSITION} — light green (an open long position).</li>
 *   <li>{@code SHORT_POSITION} — light red (an open short position).</li>
 *   <li>{@code NEUTRAL} — light blue/gray (informational band).</li>
 *   <li>{@code CAUTION} — light yellow/amber (warning band).</li>
 * </ul>
 */
public enum FillColor {
    LONG_POSITION,
    SHORT_POSITION,
    NEUTRAL,
    CAUTION
}
