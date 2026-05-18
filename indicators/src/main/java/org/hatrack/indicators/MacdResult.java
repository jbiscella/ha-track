package org.hatrack.indicators;

import java.math.BigDecimal;

/**
 * The three component series of a MACD computation. Each array is indexed by
 * bar; entries are {@code null} before the component's warm-up window.
 *
 * @param macdLine   {@code EMA(fast) - EMA(slow)}
 * @param signalLine {@code EMA(signal, macdLine)}
 * @param histogram  {@code macdLine - signalLine}
 */
public record MacdResult(BigDecimal[] macdLine, BigDecimal[] signalLine, BigDecimal[] histogram) {
}
