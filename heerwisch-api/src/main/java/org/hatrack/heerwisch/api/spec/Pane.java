package org.hatrack.heerwisch.api.spec;

/**
 * Chart pane slots. {@code MAIN} carries the price series; the eight subplot
 * slots are the hard ceiling beyond which charts become unreadable.
 */
public enum Pane {
    MAIN,
    SUBPLOT_1,
    SUBPLOT_2,
    SUBPLOT_3,
    SUBPLOT_4,
    SUBPLOT_5,
    SUBPLOT_6,
    SUBPLOT_7,
    SUBPLOT_8
}
