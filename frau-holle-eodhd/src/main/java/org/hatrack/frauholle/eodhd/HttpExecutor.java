package org.hatrack.frauholle.eodhd;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Small HTTP-client abstraction injected into {@link EodhdMarketDataSource}, so
 * the driver does not pin a specific HTTP library. A JDK-only default
 * implementation is provided.
 */
public interface HttpExecutor {

    /**
     * Performs an HTTP GET. A transport failure (timeout, connection error)
     * is signalled by throwing {@link IOException}; an HTTP error status is
     * returned as a normal {@link HttpResult}.
     */
    HttpResult get(String url, Map<String, String> headers, Duration timeout) throws IOException;
}
