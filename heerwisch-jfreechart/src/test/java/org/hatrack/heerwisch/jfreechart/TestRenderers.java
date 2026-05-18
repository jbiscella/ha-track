package org.hatrack.heerwisch.jfreechart;

import org.hatrack.heerwisch.api.error.DriverInternalException;

/**
 * Test seam: exposes the package-private font-resource constructor of
 * {@link JFreeChartRenderer} to the step definitions (a different package),
 * without leaking font customization into the public API.
 */
public final class TestRenderers {

    private TestRenderers() {
    }

    public static JFreeChartRenderer withFontResource(String resource) throws DriverInternalException {
        return new JFreeChartRenderer(resource);
    }
}
