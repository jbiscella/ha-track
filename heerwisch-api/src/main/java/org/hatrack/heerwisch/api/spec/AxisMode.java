package org.hatrack.heerwisch.api.spec;

/**
 * How the chart's horizontal (domain) axis treats time.
 *
 * <ul>
 *   <li>{@link #ORDINAL} — bars are equally spaced by position (bar index);
 *       non-trading periods (weekends, overnight, halts) take no horizontal
 *       space, so an indicator line never draws a misleading slope across a
 *       gap. Date labels are placed at period boundaries. This is the default
 *       and matches mainstream trading platforms (TradingView, MetaTrader, …).</li>
 *   <li>{@link #TIME} — the axis is literally proportional to elapsed wall-clock
 *       time; gaps render as empty stretches. Use when proportional duration
 *       matters more than the trading-platform look.</li>
 * </ul>
 */
public enum AxisMode {
    /** Equally-spaced bars; non-trading gaps are collapsed. Default. */
    ORDINAL,
    /** Time-proportional axis; gaps render as empty horizontal space. */
    TIME
}
