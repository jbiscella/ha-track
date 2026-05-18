package org.hatrack.heerwisch.api.error;

/**
 * Thrown by {@code ChartRenderer.render()} when the chosen driver does not
 * support a requested feature.
 */
public final class UnsupportedFeatureException extends ChartRenderException {

    private final String featureName;
    private final String driverName;

    public UnsupportedFeatureException(String featureName, String driverName) {
        super("feature [" + featureName + "] is not supported by driver [" + driverName + "]");
        this.featureName = featureName;
        this.driverName = driverName;
    }

    public String featureName() {
        return featureName;
    }

    public String driverName() {
        return driverName;
    }
}
