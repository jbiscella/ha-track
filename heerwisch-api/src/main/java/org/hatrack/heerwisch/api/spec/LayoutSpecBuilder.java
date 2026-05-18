package org.hatrack.heerwisch.api.spec;

import org.hatrack.heerwisch.api.error.InvalidChartSpecException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for {@link LayoutSpec}. Producing an {@code ExplicitLayoutSpec}
 * requires a main-pane height to be set; otherwise an {@code AutoLayoutSpec} is
 * produced. The default format is {@link ImageFormat#JPEG}.
 */
public final class LayoutSpecBuilder {

    private int widthPx = 900;
    private int heightPx = 500;
    private ImageFormat format = ImageFormat.JPEG;
    private BigDecimal mainPaneHeight;
    private final Map<Pane, BigDecimal> subplotHeights = new LinkedHashMap<>();

    public LayoutSpecBuilder withSize(int widthPx, int heightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        return this;
    }

    public LayoutSpecBuilder withFormat(ImageFormat format) {
        this.format = Objects.requireNonNull(format, "format");
        return this;
    }

    public LayoutSpecBuilder withMainPaneHeight(BigDecimal mainPaneHeight) {
        this.mainPaneHeight = Objects.requireNonNull(mainPaneHeight, "mainPaneHeight");
        return this;
    }

    public LayoutSpecBuilder addSubplotHeight(Pane pane, BigDecimal height) {
        subplotHeights.put(Objects.requireNonNull(pane, "pane"),
                Objects.requireNonNull(height, "height"));
        return this;
    }

    /**
     * Builds the {@link LayoutSpec}. Validates eagerly (rule V14): subplot
     * heights set without a main-pane height is a domain error reported as
     * {@link InvalidChartSpecException}, not a {@code NullPointerException}.
     */
    public LayoutSpec build() throws InvalidChartSpecException {
        if (!subplotHeights.isEmpty() && mainPaneHeight == null) {
            throw new InvalidChartSpecException("V14",
                    "explicit subplot heights were set without a main-pane height");
        }
        if (mainPaneHeight != null || !subplotHeights.isEmpty()) {
            return new LayoutSpec.ExplicitLayoutSpec(widthPx, heightPx, mainPaneHeight,
                    subplotHeights, format);
        }
        return new LayoutSpec.AutoLayoutSpec(widthPx, heightPx, format);
    }
}
