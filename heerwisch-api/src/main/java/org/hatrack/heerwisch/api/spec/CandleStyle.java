package org.hatrack.heerwisch.api.spec;

/**
 * How the price pane draws its candles from the supplied {@code OHLCSeries}.
 *
 * <p>This affects <em>only</em> the candle bodies. Overlay indicators (SMA, EMA,
 * RSI, MACD, …) and the volume pane are always computed from the real series
 * (their declared {@code PriceSource} — the source of truth), regardless of the
 * candle style. So a Heikin-Ashi chart still shows a standard, real-price RSI,
 * matching mainstream platform convention (TradingView, AvaTrade): Heikin-Ashi
 * is a visualization of price, while indicators reflect the true underlying
 * series.
 *
 * <ul>
 *   <li>{@link #OHLC} — draw raw open/high/low/close candles. Default.</li>
 *   <li>{@link #HEIKIN_ASHI} — draw Heikin-Ashi candles, derived from the
 *       supplied {@code OHLCSeries} via the standard HA transform. Has no effect
 *       when an {@code HASeries} is supplied directly (it is already HA).</li>
 * </ul>
 */
public enum CandleStyle {
    /** Raw OHLC candles. Default. */
    OHLC,
    /** Heikin-Ashi candles derived from the supplied OHLC series. */
    HEIKIN_ASHI
}
