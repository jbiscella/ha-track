package org.hatrack.indicators;

import java.math.BigDecimal;

/**
 * The three component series of a Bollinger Bands computation. Each array is
 * indexed by bar; entries are {@code null} before the warm-up window.
 *
 * @param upper  {@code middle + multiplier * stdDev}
 * @param middle the {@code SMA} of the source series
 * @param lower  {@code middle - multiplier * stdDev}
 */
public record BollingerBands(BigDecimal[] upper, BigDecimal[] middle, BigDecimal[] lower) {
}
