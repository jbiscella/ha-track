package org.hatrack.commons;

/**
 * Pivot-point calculation method. Relocated to {@code commons} in 0.52.0-alpha
 * (was {@code org.hatrack.heerwisch.api.spec.PivotPointVariant} through
 * 0.51.0-alpha) so both the plotting driver and {@code nachtkrapp} can share a
 * single definition without a cross-module cycle.
 */
public enum PivotPointVariant {
    STANDARD,
    CAMARILLA,
    WOODIE
}
