package org.hatrack.heerwisch.api.spec;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Closed hierarchy describing chart dimensions, pane heights and output
 * format. Variants are nested records.
 */
public sealed interface LayoutSpec {

    int widthPx();

    int heightPx();

    ImageFormat format();

    /** Default layout: 900x500 auto layout, PNG output. */
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
    record AutoLayoutSpec(int widthPx, int heightPx, ImageFormat format) implements LayoutSpec {
        public AutoLayoutSpec {
            requireMinDimension(widthPx, "widthPx");
            requireMinDimension(heightPx, "heightPx");
            Objects.requireNonNull(format, "format");
        }
    }

    /** Caller controls pane heights; the heights must sum to 1.0 (checked by ChartSpecBuilder, V10). */
    record ExplicitLayoutSpec(int widthPx, int heightPx, BigDecimal mainPaneHeight,
                              Map<Pane, BigDecimal> subplotHeights, ImageFormat format)
            implements LayoutSpec {
        public ExplicitLayoutSpec {
            requireMinDimension(widthPx, "widthPx");
            requireMinDimension(heightPx, "heightPx");
            Objects.requireNonNull(mainPaneHeight, "mainPaneHeight");
            Objects.requireNonNull(subplotHeights, "subplotHeights");
            Objects.requireNonNull(format, "format");
            subplotHeights = Map.copyOf(subplotHeights);
        }
    }
}
