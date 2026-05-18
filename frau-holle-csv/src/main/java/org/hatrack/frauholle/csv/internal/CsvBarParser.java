package org.hatrack.frauholle.csv.internal;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.error.MarketDataSchemaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the canonical OHLC CSV format (frau-holle-csv/CLAUDE.md sections 1, 4)
 * into {@link OHLCBar} instances. JDK-only; no external CSV library. The parser
 * validates structure (F1-F5) but not OHLC invariants, ordering or uniqueness.
 */
public final class CsvBarParser {

    private static final List<String> REQUIRED_COLUMNS =
            List.of("time", "open", "high", "low", "close");

    private CsvBarParser() {
    }

    public static List<OHLCBar> parse(List<String> lines, String symbol)
            throws MarketDataSchemaException {
        Map<String, Integer> columns = null;
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = raw.split(",", -1);
            if (columns == null) {
                columns = parseHeader(tokens, symbol);
            } else {
                bars.add(parseRow(tokens, columns, lineNumber, symbol));
            }
        }
        if (columns == null) {
            throw new MarketDataSchemaException(symbol, "CSV file has no header row");
        }
        return bars;
    }

    private static Map<String, Integer> parseHeader(String[] tokens, String symbol)
            throws MarketDataSchemaException {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < tokens.length; i++) {
            columns.put(tokens[i].trim().toLowerCase(Locale.ROOT), i);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                throw new MarketDataSchemaException(symbol,
                        "missing required column: " + required);
            }
        }
        return columns;
    }

    private static OHLCBar parseRow(String[] tokens, Map<String, Integer> columns,
                                    int lineNumber, String symbol) throws MarketDataSchemaException {
        for (String required : REQUIRED_COLUMNS) {
            if (columns.get(required) >= tokens.length) {
                throw new MarketDataSchemaException(symbol,
                        "line " + lineNumber + ": missing column '" + required + "'");
            }
        }
        Instant time = parseTime(tokens[columns.get("time")].trim(), lineNumber, symbol);
        BigDecimal open = parsePrice(tokens[columns.get("open")].trim(), "open", lineNumber, symbol);
        BigDecimal high = parsePrice(tokens[columns.get("high")].trim(), "high", lineNumber, symbol);
        BigDecimal low = parsePrice(tokens[columns.get("low")].trim(), "low", lineNumber, symbol);
        BigDecimal close = parsePrice(tokens[columns.get("close")].trim(), "close", lineNumber, symbol);
        Optional<BigDecimal> volume = parseVolume(tokens, columns, lineNumber, symbol);
        return new OHLCBar(time, open, high, low, close, volume);
    }

    private static Instant parseTime(String value, int lineNumber, String symbol)
            throws MarketDataSchemaException {
        if (!value.endsWith("Z")) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": timestamp must be a UTC instant ending in 'Z': '"
                            + value + "'");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": invalid timestamp '" + value + "'", e);
        }
    }

    private static BigDecimal parsePrice(String value, String column, int lineNumber, String symbol)
            throws MarketDataSchemaException {
        BigDecimal price;
        try {
            price = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": invalid " + column + " value '" + value + "'", e);
        }
        if (price.signum() < 0) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": " + column + " must be non-negative, was '" + value + "'");
        }
        return price;
    }

    private static Optional<BigDecimal> parseVolume(String[] tokens, Map<String, Integer> columns,
                                                    int lineNumber, String symbol)
            throws MarketDataSchemaException {
        Integer index = columns.get("volume");
        if (index == null || index >= tokens.length) {
            return Optional.empty();
        }
        String value = tokens[index].trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal volume;
        try {
            volume = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": invalid volume value '" + value + "'", e);
        }
        if (volume.signum() < 0) {
            throw new MarketDataSchemaException(symbol,
                    "line " + lineNumber + ": volume must be non-negative, was '" + value + "'");
        }
        return Optional.of(volume);
    }
}
