package org.hatrack.heerwisch.jfreechart;

import org.hatrack.heerwisch.api.spec.FillColor;
import org.hatrack.heerwisch.jfreechart.theme.ThemeConstants;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit coverage for {@link JFreeChartRenderer#horizontalLevelColor} — the
 * FillColor → semantic line color mapping. The rendered line color is not
 * recoverable from the encoded PNG, so the pure mapping is tested directly.
 */
class HorizontalLevelColorTest {

    @Test
    void eachFillColorMapsToItsSemanticConstant() {
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_WIN,
                JFreeChartRenderer.horizontalLevelColor(FillColor.WIN));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_LOSS,
                JFreeChartRenderer.horizontalLevelColor(FillColor.LOSS));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_OPEN,
                JFreeChartRenderer.horizontalLevelColor(FillColor.OPEN));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_LONG_POSITION,
                JFreeChartRenderer.horizontalLevelColor(FillColor.LONG_POSITION));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_SHORT_POSITION,
                JFreeChartRenderer.horizontalLevelColor(FillColor.SHORT_POSITION));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_NEUTRAL,
                JFreeChartRenderer.horizontalLevelColor(FillColor.NEUTRAL));
        assertEquals(ThemeConstants.HORIZONTAL_LEVEL_CAUTION,
                JFreeChartRenderer.horizontalLevelColor(FillColor.CAUTION));
    }

    @Test
    void entryStopTakeColorsAreDistinct() {
        Color entry = JFreeChartRenderer.horizontalLevelColor(FillColor.NEUTRAL);
        Color stop = JFreeChartRenderer.horizontalLevelColor(FillColor.LOSS);
        Color take = JFreeChartRenderer.horizontalLevelColor(FillColor.WIN);
        assertNotEquals(entry, stop, "entry vs stop must differ");
        assertNotEquals(entry, take, "entry vs take must differ");
        assertNotEquals(stop, take, "stop vs take must differ");
    }

    @Test
    void neutralIsNotWhiteSoItReadsOnTheWhiteCanvas() {
        Color neutral = JFreeChartRenderer.horizontalLevelColor(FillColor.NEUTRAL);
        assertNotEquals(Color.WHITE.getRGB(), neutral.getRGB(),
                "NEUTRAL must not be white — the chart background is white");
    }

    @Test
    void semanticLinesUseStrongerAlphaThanTheDefaultReference() {
        // Default reference line is ~60% alpha (153); semantic lines are ~80% (204).
        assertEquals(204, JFreeChartRenderer.horizontalLevelColor(FillColor.WIN).getAlpha());
        assertEquals(153, ThemeConstants.HORIZONTAL_LEVEL.getAlpha());
    }
}
