package org.hatrack.heerwisch.api.spec;

/**
 * Shape of the marker glyph drawn by an {@link Annotation.EntryExitMarker}.
 * The shape is purely visual — it does not imply direction by itself; the
 * accompanying {@link MarkerDirection} carries the semantic.
 */
public enum GlyphStyle {
    UP_TRIANGLE,
    DOWN_TRIANGLE,
    ARROW_UP,
    ARROW_DOWN
}
