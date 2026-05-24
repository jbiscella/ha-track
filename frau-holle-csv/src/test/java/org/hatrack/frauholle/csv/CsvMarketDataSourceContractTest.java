package org.hatrack.frauholle.csv;

import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.contract.MarketDataSourceContract;
import org.hatrack.frauholle.port.MarketDataSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Runs the shared {@link MarketDataSourceContract} against the CSV driver. */
class CsvMarketDataSourceContractTest extends MarketDataSourceContract {

    private static final String SYMBOL = "AAPL";
    private static final Timeframe TIMEFRAME = Timeframe.fromWire("1d");

    @TempDir
    Path baseDir;

    @Override
    protected MarketDataSource source() {
        try {
            Files.writeString(baseDir.resolve(SYMBOL + "_1d.csv"),
                    "time,open,high,low,close,volume\n"
                            + "2024-01-02T00:00:00Z,100,101,99,100.5,1000\n"
                            + "2024-01-03T00:00:00Z,100.5,102,100,101.5,1100\n"
                            + "2024-01-04T00:00:00Z,101.5,103,101,102.5,1200\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CsvMarketDataSource(baseDir);
    }

    @Override
    protected Query populatedQuery() {
        return new Query(SYMBOL, TIMEFRAME,
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-01-04T00:00:00Z"));
    }

    @Override
    protected Query emptyQuery() {
        return new Query(SYMBOL, TIMEFRAME,
                Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-12-31T00:00:00Z"));
    }
}
