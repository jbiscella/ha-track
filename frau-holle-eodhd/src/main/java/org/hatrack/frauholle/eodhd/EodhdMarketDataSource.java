package org.hatrack.frauholle.eodhd;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.eodhd.internal.DefaultJsonReader;
import org.hatrack.frauholle.eodhd.internal.JdkHttpExecutor;
import org.hatrack.frauholle.error.MarketDataException;
import org.hatrack.frauholle.error.MarketDataNotFoundException;
import org.hatrack.frauholle.error.MarketDataSchemaException;
import org.hatrack.frauholle.error.MarketDataUnavailableException;
import org.hatrack.frauholle.port.MarketDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference {@link MarketDataSource} backed by the EODHD End-of-Day API.
 * Synchronous, single request per call, no internal retry or caching.
 */
public final class EodhdMarketDataSource implements MarketDataSource {

    private static final String DEFAULT_BASE_URL = "https://eodhistoricaldata.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "frau-holle-eodhd/" + resolveVersion();

    private final String apiToken;
    private final String baseUrl;
    private final Duration httpTimeout;
    private final HttpExecutor httpExecutor;
    private final JsonReader jsonReader;

    /** Constructs a driver with the default base URL, 30s timeout and JDK-only defaults. */
    public EodhdMarketDataSource(String apiToken) {
        this(apiToken, DEFAULT_BASE_URL, DEFAULT_TIMEOUT,
                new JdkHttpExecutor(DEFAULT_TIMEOUT), new DefaultJsonReader());
    }

    public EodhdMarketDataSource(String apiToken, String baseUrl, Duration httpTimeout,
                                 HttpExecutor httpExecutor, JsonReader jsonReader) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("apiToken must be a non-blank string");
        }
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(httpTimeout, "httpTimeout");
        if (httpTimeout.isNegative()) {
            throw new IllegalArgumentException("httpTimeout must not be negative");
        }
        this.apiToken = apiToken;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpTimeout = httpTimeout;
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.jsonReader = Objects.requireNonNull(jsonReader, "jsonReader");
    }

    @Override
    public List<OHLCBar> fetchHistory(String symbol, Timeframe timeframe, Instant since, Instant until)
            throws MarketDataException {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");

        boolean intraday = isIntraday(timeframe);
        String url = intraday
                ? buildIntradayUrl(symbol, mapIntradayInterval(timeframe, symbol), since, until)
                : buildUrl(symbol, mapTimeframe(timeframe, symbol), since, until);

        HttpResult response;
        try {
            response = httpExecutor.get(url, requestHeaders(), httpTimeout);
        } catch (IOException e) {
            throw new MarketDataUnavailableException(symbol,
                    "HTTP request to EODHD failed: " + e.getMessage(), e);
        }

        checkStatus(response.statusCode(), symbol);
        return intraday
                ? parseIntradayBars(response.body(), symbol)
                : parseBars(response.body(), symbol);
    }

    private static boolean isIntraday(Timeframe timeframe) {
        return switch (timeframe.unit()) {
            case SECOND, MINUTE, HOUR -> true;
            case DAY, WEEK, MONTH, YEAR -> false;
        };
    }

    private static String mapTimeframe(Timeframe timeframe, String symbol)
            throws MarketDataSchemaException {
        return switch (timeframe.wire()) {
            case "1d" -> "d";
            case "1w" -> "w";
            case "1M" -> "m";
            default -> throw new MarketDataSchemaException(symbol,
                    "daily timeframe '" + timeframe.wire() + "' is not supported by EODHD; "
                            + "supported daily timeframes: {1d, 1w, 1M}");
        };
    }

    private static String mapIntradayInterval(Timeframe timeframe, String symbol)
            throws MarketDataSchemaException {
        return switch (timeframe.wire()) {
            case "1m" -> "1m";
            case "5m" -> "5m";
            case "1h" -> "1h";
            default -> throw new MarketDataSchemaException(symbol,
                    "intraday timeframe '" + timeframe.wire() + "' is not supported by EODHD; "
                            + "supported intraday intervals: {1m, 5m, 1h}");
        };
    }

    private String buildUrl(String symbol, String period, Instant since, Instant until) {
        String from = since.atOffset(ZoneOffset.UTC).toLocalDate().toString();
        String to = until.atOffset(ZoneOffset.UTC).toLocalDate().toString();
        return baseUrl + "/api/eod/" + symbol
                + "?api_token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8)
                + "&fmt=json"
                + "&from=" + from
                + "&to=" + to
                + "&period=" + period;
    }

    private String buildIntradayUrl(String symbol, String interval, Instant since, Instant until) {
        return baseUrl + "/api/intraday/" + symbol
                + "?api_token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8)
                + "&fmt=json"
                + "&interval=" + interval
                + "&from=" + since.getEpochSecond()
                + "&to=" + until.getEpochSecond();
    }

    private static Map<String, String> requestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", USER_AGENT);
        return headers;
    }

    private static void checkStatus(int statusCode, String symbol) throws MarketDataException {
        if (statusCode == 200) {
            return;
        }
        if (statusCode == 404) {
            throw new MarketDataNotFoundException(symbol,
                    "EODHD returned 404 — symbol '" + symbol + "' not found");
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new MarketDataUnavailableException(symbol,
                    "EODHD returned " + statusCode + " — authentication issue for symbol '"
                            + symbol + "' (the API token may need to be regenerated)");
        }
        if (statusCode == 429) {
            throw new MarketDataUnavailableException(symbol,
                    "EODHD returned 429 — rate limit exceeded for symbol '" + symbol + "'");
        }
        if (statusCode >= 500) {
            throw new MarketDataUnavailableException(symbol,
                    "EODHD returned server error " + statusCode + " for symbol '" + symbol + "'");
        }
        throw new MarketDataUnavailableException(symbol,
                "EODHD returned unexpected status " + statusCode + " for symbol '" + symbol + "'");
    }

    private List<OHLCBar> parseBars(String body, String symbol) throws MarketDataException {
        List<Map<String, String>> rows;
        try {
            rows = jsonReader.readArrayOfObjects(body);
        } catch (RuntimeException e) {
            throw new MarketDataSchemaException(symbol, "malformed JSON response from EODHD", e);
        }
        List<OHLCBar> bars = new ArrayList<>(rows.size());
        Instant previous = null;
        int index = 0;
        for (Map<String, String> row : rows) {
            OHLCBar bar = mapRow(row, symbol);
            if (previous != null && !bar.time().isAfter(previous)) {
                throw new MarketDataSchemaException(symbol,
                        "EODHD bars are not in strict ascending date order: row " + index
                                + " has date " + bar.time() + " which is not after the previous bar");
            }
            previous = bar.time();
            bars.add(bar);
            index++;
        }
        return List.copyOf(bars);
    }

    private static OHLCBar mapRow(Map<String, String> row, String symbol)
            throws MarketDataSchemaException {
        Instant time = parseDate(required(row, "date", symbol), symbol);
        BigDecimal open = parsePrice(required(row, "open", symbol), "open", symbol);
        BigDecimal high = parsePrice(required(row, "high", symbol), "high", symbol);
        BigDecimal low = parsePrice(required(row, "low", symbol), "low", symbol);
        BigDecimal close = parsePrice(required(row, "close", symbol), "close", symbol);
        Optional<BigDecimal> volume = parseVolume(row.get("volume"), symbol);
        return new OHLCBar(time, open, high, low, close, volume);
    }

    private List<OHLCBar> parseIntradayBars(String body, String symbol) throws MarketDataException {
        List<Map<String, String>> rows;
        try {
            rows = jsonReader.readArrayOfObjects(body);
        } catch (RuntimeException e) {
            throw new MarketDataSchemaException(symbol, "malformed JSON response from EODHD", e);
        }
        List<OHLCBar> bars = new ArrayList<>(rows.size());
        Instant previous = null;
        int index = 0;
        for (Map<String, String> row : rows) {
            OHLCBar bar = mapIntradayRow(row, symbol);
            if (previous != null && !bar.time().isAfter(previous)) {
                throw new MarketDataSchemaException(symbol,
                        "EODHD intraday bars are not in strict ascending timestamp order: row "
                                + index + " has time " + bar.time()
                                + " which is not after the previous bar");
            }
            previous = bar.time();
            bars.add(bar);
            index++;
        }
        return List.copyOf(bars);
    }

    private static OHLCBar mapIntradayRow(Map<String, String> row, String symbol)
            throws MarketDataSchemaException {
        Instant time = parseTimestamp(required(row, "timestamp", symbol), symbol);
        BigDecimal open = parsePrice(required(row, "open", symbol), "open", symbol);
        BigDecimal high = parsePrice(required(row, "high", symbol), "high", symbol);
        BigDecimal low = parsePrice(required(row, "low", symbol), "low", symbol);
        BigDecimal close = parsePrice(required(row, "close", symbol), "close", symbol);
        Optional<BigDecimal> volume = parseVolume(row.get("volume"), symbol);
        return new OHLCBar(time, open, high, low, close, volume);
    }

    private static Instant parseTimestamp(String text, String symbol)
            throws MarketDataSchemaException {
        try {
            return Instant.ofEpochSecond(Long.parseLong(text));
        } catch (NumberFormatException | DateTimeException e) {
            throw new MarketDataSchemaException(symbol,
                    "invalid timestamp '" + text + "' in EODHD intraday bar (expected unix seconds)", e);
        }
    }

    private static String required(Map<String, String> row, String field, String symbol)
            throws MarketDataSchemaException {
        String value = row.get(field);
        if (value == null) {
            throw new MarketDataSchemaException(symbol,
                    "EODHD bar object is missing the '" + field + "' field");
        }
        return value;
    }

    private static Instant parseDate(String date, String symbol) throws MarketDataSchemaException {
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new MarketDataSchemaException(symbol, "invalid date '" + date + "' in EODHD bar", e);
        }
    }

    private static BigDecimal parsePrice(String text, String field, String symbol)
            throws MarketDataSchemaException {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new MarketDataSchemaException(symbol,
                    "invalid " + field + " value '" + text + "' in EODHD bar", e);
        }
    }

    private static Optional<BigDecimal> parseVolume(String text, String symbol)
            throws MarketDataSchemaException {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(text));
        } catch (NumberFormatException e) {
            throw new MarketDataSchemaException(symbol,
                    "invalid volume value '" + text + "' in EODHD bar", e);
        }
    }

    private static String resolveVersion() {
        String version = EodhdMarketDataSource.class.getPackage().getImplementationVersion();
        return version != null ? version : "1.0.0-SNAPSHOT";
    }
}
