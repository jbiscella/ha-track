package org.hatrack.heerwisch.api.steps;

import org.hatrack.heerwisch.api.port.ChartRenderer;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A minimal conformant {@link ChartRenderer} used to exercise the port
 * contract in heerwisch-api tests. Deterministic: the same spec always
 * yields byte-identical output.
 */
final class FakeChartRenderer implements ChartRenderer {

    @Override
    public ChartImage render(ChartSpec spec) {
        Objects.requireNonNull(spec, "spec");
        byte[] bytes = ("CHART indicators=" + spec.indicators().size()
                + " annotations=" + spec.annotations().size())
                .getBytes(StandardCharsets.UTF_8);
        return new ChartImage(bytes, "image/png",
                spec.layout().widthPx(), spec.layout().heightPx());
    }
}
