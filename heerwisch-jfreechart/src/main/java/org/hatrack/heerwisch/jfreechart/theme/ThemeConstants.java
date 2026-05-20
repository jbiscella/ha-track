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
    public static final Color FIB_LEVEL = new Color(0x7B, 0x1F, 0xA2, 153);
    public static final Color PIVOT_LEVEL = new Color(0x19, 0x76, 0xD2, 153);

    // Base (opaque) colors for TimeRangeHighlight fills. The per-instance
    // opacity is applied by JFreeChartRenderer; these are RGB-only.
    public static final Color TIME_RANGE_LONG = new Color(0x26A69A);
    public static final Color TIME_RANGE_SHORT = new Color(0xEF5350);
    public static final Color TIME_RANGE_NEUTRAL = new Color(0x90A4AE);
    public static final Color TIME_RANGE_CAUTION = new Color(0xFFB300);

    public static final Stroke STROKE_DEFAULT = new BasicStroke(1.0f);
    public static final Stroke STROKE_INDICATOR = new BasicStroke(1.5f);
    public static final Stroke STROKE_HORIZONTAL_LEVEL_DASHED = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {5.0f, 3.0f}, 0.0f);
    public static final Stroke STROKE_HORIZONTAL_LEVEL_DOTTED = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {1.0f, 2.0f}, 0.0f);
}
