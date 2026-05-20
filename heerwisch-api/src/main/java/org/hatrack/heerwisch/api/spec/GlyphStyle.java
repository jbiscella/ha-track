package org.hatrack.heerwisch.api.spec;

/**
 * Glyph shape for an {@link Annotation.EntryExitMarker}. The choice between
 * TRIANGLE and ARROW carries a documented semantic contract, not just an
 * aesthetic preference:
 *
 * <ul>
 *   <li>{@link #UP_TRIANGLE} / {@link #DOWN_TRIANGLE} indicate a
 *       <b>scheduled</b> entry or exit: the trade event was triggered by an
 *       explicit strategy scenario (an entry/exit condition the strategy
 *       author authored as a deliberate trade decision).</li>
 *   <li>{@link #ARROW_UP} / {@link #ARROW_DOWN} indicate a <b>forced</b>
 *       entry or exit: the trade event was triggered by mechanical risk
 *       management (stop-loss, take-profit, trailing stop, time-based exit,
 *       end-of-backtest forced close).</li>
 * </ul>
 *
 * Consumers should choose TRIANGLE for scenario-driven events and ARROW for
 * risk-managed or otherwise forced events. The renderer's native geometry
 * reinforces the distinction: triangles render as compact solid shapes
 * (visually prominent, signaling a deliberate decision), while arrows render
 * as a thin chevron+shaft silhouette (visually lighter, signaling mechanical
 * execution). The asymmetry is intentional and part of the API contract.
 *
 * <p>The accompanying {@link MarkerDirection} carries the long/short
 * direction; this enum carries only shape and scheduled-vs-forced intent.
 */
public enum GlyphStyle {
    UP_TRIANGLE,
    DOWN_TRIANGLE,
    ARROW_UP,
    ARROW_DOWN
}
