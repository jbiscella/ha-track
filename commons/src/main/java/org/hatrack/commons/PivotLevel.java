package org.hatrack.commons;

/**
 * The pivot levels a {@link PivotPointVariant} can produce. Not every variant
 * produces every level — {@code STANDARD} has no R4/S4, {@code WOODIE} has no
 * R3/R4/S3/S4, and {@code CAMARILLA} has no central pivot {@code P}. The
 * per-variant validity is documented in {@link PivotPoints}.
 */
public enum PivotLevel {
    P,
    R1,
    R2,
    R3,
    R4,
    S1,
    S2,
    S3,
    S4
}
