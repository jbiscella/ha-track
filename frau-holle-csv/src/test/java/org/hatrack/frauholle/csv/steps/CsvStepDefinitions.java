package org.hatrack.frauholle.csv.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.csv.CsvMarketDataSource;
import org.hatrack.frauholle.error.MarketDataNotFoundException;
import org.hatrack.frauholle.error.MarketDataSchemaException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class CsvStepDefinitions {

    private static final Instant RANGE_START = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant RANGE_END = Instant.parse("2999-01-01T00:00:00Z");

    private Path baseDirectory;
    private CsvMarketDataSource source;
    private List<OHLCBar> result;
    private Exception thrown;

    @After
    public void cleanUp() throws IOException {
        if (baseDirectory != null && Files.exists(baseDirectory)) {
            try (Stream<Path> paths = Files.walk(baseDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    @Given("a base directory")
    public void aBaseDirectory() throws IOException {
        baseDirectory = Files.createTempDirectory("frau-holle-csv");
        source = null;
        result = null;
        thrown = null;
    }

    @Given("a CSV file {string} with content:")
    public void aCsvFileWithContent(String name, String content) throws IOException {
        Path file = baseDirectory.resolve(name);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Given("a CSV data source with the default pattern")
    public void aCsvDataSourceWithTheDefaultPattern() {
        source = new CsvMarketDataSource(baseDirectory);
    }

    @Given("a CSV data source with pattern {string}")
    public void aCsvDataSourceWithPattern(String pattern) {
        source = new CsvMarketDataSource(baseDirectory, pattern);
    }

    @When("I fetch history for {string} {string} over the full range")
    public void iFetchHistoryOverTheFullRange(String symbol, String timeframe) {
        fetch(symbol, timeframe, RANGE_START, RANGE_END);
    }

    @When("I fetch history for {string} {string} from {string} to {string}")
    public void iFetchHistoryFromTo(String symbol, String timeframe, String since, String until) {
        fetch(symbol, timeframe, Instant.parse(since), Instant.parse(until));
    }

    @When("I construct a CSV data source with pattern {string}")
    public void iConstructACsvDataSourceWithPattern(String pattern) {
        thrown = null;
        try {
            source = new CsvMarketDataSource(baseDirectory, pattern);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the result has {int} bar(s)")
    public void theResultHasBars(int count) {
        requireResult();
        assertTrue(result.size() == count,
                "bar count: expected " + count + " but was " + result.size());
    }

    @Then("bar {int} has time {string}")
    public void barHasTime(int index, String time) {
        requireResult();
        assertTrue(result.get(index).time().equals(Instant.parse(time)),
                "bar " + index + " time: expected " + time + " but was " + result.get(index).time());
    }

    @Then("bar {int} has close {bigdecimal}")
    public void barHasClose(int index, BigDecimal close) {
        requireResult();
        assertTrue(close.compareTo(result.get(index).close()) == 0,
                "bar " + index + " close: expected " + close + " but was " + result.get(index).close());
    }

    @Then("bar {int} has volume {bigdecimal}")
    public void barHasVolume(int index, BigDecimal volume) {
        requireResult();
        OHLCBar bar = result.get(index);
        assertTrue(bar.volume().isPresent(), "bar " + index + " has no volume");
        assertTrue(volume.compareTo(bar.volume().get()) == 0,
                "bar " + index + " volume: expected " + volume + " but was " + bar.volume().get());
    }

    @Then("bar {int} has no volume")
    public void barHasNoVolume(int index) {
        requireResult();
        assertTrue(result.get(index).volume().isEmpty(),
                "bar " + index + " unexpectedly has a volume");
    }

    @Then("every bar has no volume")
    public void everyBarHasNoVolume() {
        requireResult();
        for (OHLCBar bar : result) {
            assertTrue(bar.volume().isEmpty(), "a bar unexpectedly has a volume");
        }
    }

    @Then("no exception is thrown")
    public void noExceptionIsThrown() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
    }

    @Then("a MarketDataNotFoundException is thrown")
    public void aMarketDataNotFoundExceptionIsThrown() {
        assertTrue(thrown instanceof MarketDataNotFoundException,
                "expected MarketDataNotFoundException but was " + thrown);
    }

    @Then("a MarketDataSchemaException is thrown")
    public void aMarketDataSchemaExceptionIsThrown() {
        assertTrue(thrown instanceof MarketDataSchemaException,
                "expected MarketDataSchemaException but was " + thrown);
    }

    @Then("an IllegalArgumentException is thrown")
    public void anIllegalArgumentExceptionIsThrown() {
        assertTrue(thrown instanceof IllegalArgumentException,
                "expected IllegalArgumentException but was " + thrown);
    }

    @Then("the exception message mentions {string}")
    public void theExceptionMessageMentions(String fragment) {
        assertTrue(thrown != null, "no exception was thrown");
        String message = thrown.getMessage();
        assertTrue(message != null && message.contains(fragment),
                "exception message [" + message + "] does not mention '" + fragment + "'");
    }

    private void fetch(String symbol, String timeframe, Instant since, Instant until) {
        thrown = null;
        result = null;
        try {
            result = source.fetchHistory(symbol, Timeframe.fromWire(timeframe), since, until);
        } catch (Exception e) {
            thrown = e;
        }
    }

    private void requireResult() {
        if (thrown != null) {
            throw new AssertionError("fetch failed unexpectedly: " + thrown, thrown);
        }
        assertTrue(result != null, "no fetch result available");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
