package org.hatrack.frauholle.csv;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.csv.internal.CsvBarParser;
import org.hatrack.frauholle.error.MarketDataException;
import org.hatrack.frauholle.error.MarketDataNotFoundException;
import org.hatrack.frauholle.error.MarketDataUnavailableException;
import org.hatrack.frauholle.port.MarketDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reference {@link MarketDataSource} that reads OHLC bars from local CSV files.
 * Read-only, JDK-only, deterministic. The driver does not sort, does not check
 * OHLC invariants and does not police ordering — those are downstream concerns
 * of {@code frau-holle.BacktestSpec.builder()}.
 */
public final class CsvMarketDataSource implements MarketDataSource {

    private static final String DEFAULT_FILE_NAME_PATTERN = "{symbol}_{timeframe}.csv";

    private final Path baseDirectory;
    private final String fileNamePattern;

    /** Uses the default file-name pattern {@code "{symbol}_{timeframe}.csv"}. */
    public CsvMarketDataSource(Path baseDirectory) {
        this(baseDirectory, DEFAULT_FILE_NAME_PATTERN);
    }

    /**
     * @param fileNamePattern template with placeholders {@code {symbol}} and
     *                        {@code {timeframe}}; MUST contain {@code {symbol}}.
     */
    public CsvMarketDataSource(Path baseDirectory, String fileNamePattern) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
        this.fileNamePattern = Objects.requireNonNull(fileNamePattern, "fileNamePattern");
        if (!fileNamePattern.contains("{symbol}")) {
            throw new IllegalArgumentException(
                    "fileNamePattern must contain the {symbol} placeholder: " + fileNamePattern);
        }
    }

    @Override
    public List<OHLCBar> fetchHistory(String symbol, Timeframe timeframe, Instant since, Instant until)
            throws MarketDataException {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");

        Path file = baseDirectory.resolve(fileNamePattern
                .replace("{symbol}", symbol)
                .replace("{timeframe}", timeframe.wire()));
        if (!Files.isRegularFile(file)) {
            throw new MarketDataNotFoundException(symbol,
                    "CSV file not found for symbol '" + symbol + "': " + file);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MarketDataUnavailableException(symbol,
                    "failed to read CSV file: " + file, e);
        }

        List<OHLCBar> bars = CsvBarParser.parse(lines, symbol);
        List<OHLCBar> filtered = new ArrayList<>();
        for (OHLCBar bar : bars) {
            if (bar.time().compareTo(since) >= 0 && bar.time().compareTo(until) <= 0) {
                filtered.add(bar);
            }
        }
        return List.copyOf(filtered);
    }
}
