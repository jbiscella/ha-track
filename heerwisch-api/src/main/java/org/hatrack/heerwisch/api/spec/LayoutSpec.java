package org.hatrack.heerwisch.api.spec;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Closed hierarchy describing chart dimensions, pane heights, output format and
 * domain-axis mode. Variants are nested records.
 */
public sealed interface LayoutSpec {

    int widthPx();

    int heightPx();

    ImageFormat format();

    /** How the domain axis treats time (gap-collapsing ordinal vs time-proportional). */
    AxisMode axisMode();

    /** Default layout: 900x500 auto layout, PNG output, ordinal (gap-collapsing) axis. */
    static LayoutSpec defaults() {
        return new AutoLayoutSpec(900, 500, ImageFormat.PNG);
    }

    static LayoutSpecBuilder builder() {
        return new LayoutSpecBuilder();
    }

    private static int requireMinDimension(int value, String name) {
        if (value < 100) {
            throw new IllegalArgumentException(name + " must be >= 100, was " + value);
        }
        return value;
    }

    /** Driver auto-distributes pane heights (main 60%, subplots share 40%). */
    record AutoLayoutSpec(int widthPx, int heightPx, ImageFormat format, AxisMode axisMode)
            implements LayoutSpec {
        public AutoLayoutSpec {
            requireMinDimension(widthPx, "widthPx");
            requireMinDimension(heightPx, "heightPx");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(axisMode, "axisMode");
        }

        /** Backward-compatible: defaults {@code axisMode} to {@link AxisMode#ORDINAL}. */
        public AutoLayoutSpec(int widthPx, int heightPx, ImageFormat format) {
            this(widthPx, heightPx, format, AxisMode.ORDINAL);
        }
    }

    /** Caller controls pane heights; the heights must sum to 1.0 (checked by ChartSpecBuilder, V10). */
    record ExplicitLayoutSpec(int widthPx, int heightPx, BigDecimal mainPaneHeight,
                              Map<Pane, BigDecimal> subplotHeights, ImageFormat format,
                              AxisMode axisMode)
            implements LayoutSpec {
        public ExplicitLayoutSpec {
            requireMinDimension(widthPx, "widthPx");
            requireMinDimension(heightPx, "heightPx");
            Objects.requireNonNull(mainPaneHeight, "mainPaneHeight");
            Objects.requireNonNull(subplotHeights, "subplotHeights");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(axisMode, "axisMode");
            subplotHeights = Map.copyOf(subplotHeights);
        }

        /** Backward-compatible: defaults {@code axisMode} to {@link AxisMode#ORDINAL}. */
        public ExplicitLayoutSpec(int widthPx, int heightPx, BigDecimal mainPaneHeight,
                                  Map<Pane, BigDecimal> subplotHeights, ImageFormat format) {
            this(widthPx, heightPx, mainPaneHeight, subplotHeights, format, AxisMode.ORDINAL);
        }
    }
}
