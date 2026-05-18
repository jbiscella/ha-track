package org.hatrack.indicators;

import java.math.BigDecimal;

/**
 * The two component series of a Stochastic Oscillator computation. Each array
 * is indexed by bar; entries are {@code null} before the warm-up window.
 *
 * @param percentK the smoothed %K line
 * @param percentD the %D line, an {@code SMA} of %K
 */
public record StochasticResult(BigDecimal[] percentK, BigDecimal[] percentD) {
}
