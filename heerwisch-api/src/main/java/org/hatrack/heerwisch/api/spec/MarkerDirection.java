package org.hatrack.heerwisch.api.spec;

/**
 * Direction tag for an {@link Annotation.EntryExitMarker}. The four values
 * cover the trade-lifecycle moments at which a glyph marker is most useful;
 * the rendering driver picks a semantic color from each value (green for
 * profitable-direction events, red for the opposite — see the
 * {@code heerwisch-jfreechart} CLAUDE spec for the canonical palette).
 *
 * <p>This enum is intentionally named {@code MarkerDirection} rather than
 * {@code Direction} to avoid a simple-name clash with
 * {@code org.hatrack.frauholle.model.Direction} ({@code LONG} / {@code SHORT})
 * in consumer code that imports both modules.
 */
public enum MarkerDirection {
    LONG_ENTRY,
    LONG_EXIT,
    SHORT_ENTRY,
    SHORT_EXIT
}
