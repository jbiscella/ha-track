package org.hatrack.heerwisch.api.port;

import org.hatrack.heerwisch.api.error.ChartRenderException;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;

/**
 * Driver port: consumes a {@link ChartSpec} and produces a {@link ChartImage}.
 * Implementations are provided by driver modules (e.g. heerwisch-jfreechart).
 *
 * <p>The contract is single-threaded; concurrent callers must serialize
 * externally. A null spec is a programmer error and throws
 * {@link NullPointerException}, not a {@link ChartRenderException}.
 */
public interface ChartRenderer {

    ChartImage render(ChartSpec spec) throws ChartRenderException;
}
