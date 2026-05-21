package org.hatrack.heerwisch.jfreechart.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

/**
 * Read-only colour palette and stroke conventions of the JFreeChart driver
 * (heerwisch-jfreechart/CLAUDE.md section 5). Consumers may read these for
 * documentation or external legend rendering; they cannot be overridden.
 */
public final class ThemeConstants {

    private ThemeConstants() {
    }

    public static final Color BACKGROUND = new Color(0xFFFFFF);
    public static final Color GRID = new Color(0xE0E0E0);
    public static final Color AXIS = new Color(0x303030);
    public static final Color TEXT = new Color(0x202020);
    public static final Color BULLISH_CANDLE = new Color(0x26A69A);
    public static final Color BEARISH_CANDLE = new Color(0xEF5350);
    public static final Color WICK = new Color(0x303030);
    public static final Color SMA_LINE = new Color(0x1976D2);
    public static final Color EMA_LINE = new Color(0xF57C00);
    public static final Color BB_BAND = new Color(0x9E9E9E);
    public static final Color BB_FILL = new Color(0x9E, 0x9E, 0x9E, 26);
    public static final Color RSI_LINE = new Color(0x7B1FA2);
    public static final Color RSI_OVERBOUGHT_LEVEL = new Color(0xEF, 0x53, 0x50, 153);
    public static final Color RSI_OVERSOLD_LEVEL = new Color(0x26, 0xA6, 0x9A, 153);
    /** Shaded fill for the overbought danger zone (above overbought threshold). 15% alpha. */
    public static final Color RSI_OVERBOUGHT_ZONE = new Color(0xEF, 0x53, 0x50, 38);
    /** Shaded fill for the oversold danger zone (below oversold threshold). 15% alpha. */
    public static final Color RSI_OVERSOLD_ZONE = new Color(0x26, 0xA6, 0x9A, 38);
    public static final Color MACD_LINE = new Color(0x1976D2);
    public static final Color MACD_SIGNAL = new Color(0xF57C00);
    public static final Color MACD_HISTOGRAM_UP = new Color(0x26, 0xA6, 0x9A, 179);
    public static final Color MACD_HISTOGRAM_DOWN = new Color(0xEF, 0x53, 0x50, 179);
    public static final Color ADX_LINE = new Color(0x7B1FA2);
    public static final Color STOCHASTIC_K = new Color(0x1976D2);
    public static final Color STOCHASTIC_D = new Color(0xF57C00);
    public static final Color ATR_LINE = new Color(0x7B1FA2);
    public static final Color VOLUME_BAR_UP = new Color(0x26, 0xA6, 0x9A, 153);
    public static final Color VOLUME_BAR_DOWN = new Color(0xEF, 0x53, 0x50, 153);
    public static final Color ANNOTATION_BULLISH = new Color(0x26A69A);
    public static final Color ANNOTATION_BEARISH = new Color(0xEF5350);
    public static final Color ANNOTATION_NEUTRAL = new Color(0x7B1FA2);
    public static final Color HORIZONTAL_LEVEL = new Color(0x30, 0x30, 0x30, 153);
    // Semantic HorizontalLevel line colors, selected via Annotation.HorizontalLevel's
    // optional FillColor. Stroked lines (not translucent fills), so a stronger 80%
    // alpha than the TimeRangeHighlight band palette. Hues align with the
    // TimeRangeHighlight base colors where they share meaning.
    /** Take-profit / winning level — teal-green. */
    public static final Color HORIZONTAL_LEVEL_WIN = new Color(0x26, 0xA6, 0x9A, 204);
    /** Stop-loss / losing level — coral-red. */
    public static final Color HORIZONTAL_LEVEL_LOSS = new Color(0xEF, 0x53, 0x50, 204);
    /** Still-open level — blue-grey. */
    public static final Color HORIZONTAL_LEVEL_OPEN = new Color(0x90, 0xA4, 0xAE, 204);
    /** Long-position level — cooler/darker teal, distinct from WIN's brighter green. */
    public static final Color HORIZONTAL_LEVEL_LONG_POSITION = new Color(0x00, 0x89, 0x7B, 204);
    /** Short-position level — warmer orange-red, distinct from LOSS's coral. */
    public static final Color HORIZONTAL_LEVEL_SHORT_POSITION = new Color(0xE6, 0x4A, 0x19, 204);
    /** Neutral reference (e.g. trade entry) — dark near-black; white would be invisible on the white canvas. */
    public static final Color HORIZONTAL_LEVEL_NEUTRAL = new Color(0x30, 0x30, 0x30, 204);
    /** Caution level — amber. */
    public static final Color HORIZONTAL_LEVEL_CAUTION = new Color(0xFF, 0xB3, 0x00, 204);
    public static final Color FIB_LEVEL = new Color(0x7B, 0x1F, 0xA2, 153);
    public static final Color PIVOT_LEVEL = new Color(0x19, 0x76, 0xD2, 153);

    // Base (opaque) colors for TimeRangeHighlight fills. The per-instance
    // opacity is applied by JFreeChartRenderer; these are RGB-only.
    public static final Color TIME_RANGE_LONG = new Color(0x26A69A);
    public static final Color TIME_RANGE_SHORT = new Color(0xEF5350);
    public static final Color TIME_RANGE_NEUTRAL = new Color(0x90A4AE);
    public static final Color TIME_RANGE_CAUTION = new Color(0xFFB300);
    /** Outcome-oriented: winning trade. Same RGB as {@code TIME_RANGE_LONG} today; the renderer may differentiate later without API change. */
    public static final Color TIME_RANGE_WIN = new Color(0x26A69A);
    /** Outcome-oriented: losing trade. Same RGB as {@code TIME_RANGE_SHORT} today; the renderer may differentiate later without API change. */
    public static final Color TIME_RANGE_LOSS = new Color(0xEF5350);
    /** Outcome-oriented: still-open trade at backtest end. Same RGB as {@code TIME_RANGE_NEUTRAL} today; the renderer may differentiate later without API change. */
    public static final Color TIME_RANGE_OPEN = new Color(0x90A4AE);

    /**
     * Vertical offset multiplier for auto-positioned {@code EntryExitMarkerAuto}
     * glyphs. The glyph sits at {@code bar.low - glyph.dy × FACTOR} for
     * {@code LONG_ENTRY} / {@code SHORT_EXIT} (below the bar), and at
     * {@code bar.high + glyph.dy × FACTOR} for {@code LONG_EXIT} /
     * {@code SHORT_ENTRY} (above the bar). Default {@code 1.5} places the
     * glyph clearly outside the bar without excessive distance.
     *
     * <p>Future variants ({@code GLYPH_OFFSET_FACTOR_TIME},
     * {@code GLYPH_OFFSET_FACTOR_PRICE}, etc.) may be added if other offset
     * semantics emerge.
     */
    public static final double GLYPH_OFFSET_FACTOR_BAR = 1.5;

    public static final Stroke STROKE_DEFAULT = new BasicStroke(1.0f);
    public static final Stroke STROKE_INDICATOR = new BasicStroke(1.5f);
    public static final Stroke STROKE_HORIZONTAL_LEVEL_DASHED = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {5.0f, 3.0f}, 0.0f);
    public static final Stroke STROKE_HORIZONTAL_LEVEL_DOTTED = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {1.0f, 2.0f}, 0.0f);
}
