package org.hatrack.frauholle.eodhd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.eodhd.EodhdMarketDataSource;
import org.hatrack.frauholle.eodhd.JsonParseException;
import org.hatrack.frauholle.eodhd.internal.DefaultJsonReader;
import org.hatrack.frauholle.error.MarketDataNotFoundException;
import org.hatrack.frauholle.error.MarketDataSchemaException;
import org.hatrack.frauholle.error.MarketDataUnavailableException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class EodhdStepDefinitions {

    private static final Instant SINCE = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2024-12-31T00:00:00Z");
    private static final String API_TOKEN = "test-token";

    private MockHttpExecutor mock;
    private EodhdMarketDataSource driver;
    private IOException timeoutException;
    private List<OHLCBar> result;
    private Exception thrown;

    // --- given ---

    @Given("an EODHD data source")
    public void anEodhdDataSource() {
        mock = new MockHttpExecutor();
        driver = new EodhdMarketDataSource(API_TOKEN, "https://eodhistoricaldata.com",
                Duration.ofSeconds(30), mock, new DefaultJsonReader());
        result = null;
        thrown = null;
    }

    @Given("the endpoint returns the JSON body:")
    public void theEndpointReturnsTheJsonBody(String body) {
        mock.respondWith(200, body);
    }

    @Given("the endpoint responds with HTTP status {int}")
    public void theEndpointRespondsWithHttpStatus(int status) {
        mock.respondWith(status, "[]");
    }

    @Given("the endpoint times out")
    public void theEndpointTimesOut() {
        timeoutException = new HttpTimeoutException("request timed out");
        mock.failWith(timeoutException);
    }

    // --- when ---

    @When("I fetch history for {string} as {string}")
    public void iFetchHistoryFor(String symbol, String timeframe) {
        thrown = null;
        result = null;
        try {
            result = driver.fetchHistory(symbol, Timeframe.fromWire(timeframe), SINCE, UNTIL);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I construct a data source with an empty API token")
    public void iConstructWithAnEmptyApiToken() {
        captureConstruction("", "https://x", Duration.ofSeconds(30), new MockHttpExecutor());
    }

    @When("I construct a data source with a null API token")
    public void iConstructWithANullApiToken() {
        captureConstruction(null, "https://x", Duration.ofSeconds(30), new MockHttpExecutor());
    }

    @When("I construct a data source with a negative HTTP timeout")
    public void iConstructWithANegativeHttpTimeout() {
        captureConstruction("test-token", "https://x", Duration.ofSeconds(-1), new MockHttpExecutor());
    }

    @When("I construct a data source with a null HTTP executor")
    public void iConstructWithANullHttpExecutor() {
        captureConstruction("test-token", "https://x", Duration.ofSeconds(30), null);
    }

    // --- then ---

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

    @Then("no exception is thrown")
    public void noExceptionIsThrown() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
    }

    @Then("a MarketDataNotFoundException is thrown")
    public void aMarketDataNotFoundExceptionIsThrown() {
        assertTrue(thrown instanceof MarketDataNotFoundException,
                "expected MarketDataNotFoundException but was " + thrown);
    }

    @Then("a MarketDataUnavailableException is thrown")
    public void aMarketDataUnavailableExceptionIsThrown() {
        assertTrue(thrown instanceof MarketDataUnavailableException,
                "expected MarketDataUnavailableException but was " + thrown);
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

    @Then("a NullPointerException is thrown")
    public void aNullPointerExceptionIsThrown() {
        assertTrue(thrown instanceof NullPointerException,
                "expected NullPointerException but was " + thrown);
    }

    @Then("the exception message mentions {string}")
    public void theExceptionMessageMentions(String fragment) {
        assertTrue(thrown != null, "no exception was thrown");
        String message = thrown.getMessage();
        assertTrue(message != null && message.contains(fragment),
                "exception message [" + message + "] does not mention '" + fragment + "'");
    }

    @Then("the exception cause is a JsonParseException")
    public void theExceptionCauseIsAJsonParseException() {
        assertTrue(thrown != null && thrown.getCause() instanceof JsonParseException,
                "expected a JsonParseException cause but was "
                        + (thrown == null ? "no exception" : thrown.getCause()));
    }

    @Then("the exception cause is the timeout exception")
    public void theExceptionCauseIsTheTimeoutException() {
        assertTrue(thrown != null && thrown.getCause() == timeoutException,
                "exception cause is not the timeout exception");
    }

    @Then("no exception in the chain reveals the API token")
    public void noExceptionInTheChainRevealsTheApiToken() {
        assertTrue(thrown != null, "no exception was thrown");
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            String message = t.getMessage();
            assertTrue(message == null || !message.contains(API_TOKEN),
                    "exception " + t.getClass().getSimpleName()
                            + " message leaks the API token: [" + message + "]");
            if (t.getCause() == t) {
                break;
            }
        }
    }

    @Then("the request URL contains {string}")
    public void theRequestUrlContains(String fragment) {
        String url = mock.lastUrl();
        assertTrue(url != null && url.contains(fragment),
                "request URL [" + url + "] does not contain '" + fragment + "'");
    }

    @Then("exactly {int} HTTP request(s) was made")
    public void exactlyHttpRequestsWasMade(int count) {
        assertTrue(mock.callCount() == count,
                "HTTP request count: expected " + count + " but was " + mock.callCount());
    }

    @Then("the User-Agent header starts with {string}")
    public void theUserAgentHeaderStartsWith(String prefix) {
        assertTrue(mock.lastHeaders() != null, "no request was sent");
        String userAgent = mock.lastHeaders().get("User-Agent");
        assertTrue(userAgent != null && userAgent.startsWith(prefix),
                "User-Agent [" + userAgent + "] does not start with '" + prefix + "'");
    }

    // --- helpers ---

    private void captureConstruction(String apiToken, String baseUrl, Duration timeout,
                                     MockHttpExecutor executor) {
        thrown = null;
        try {
            new EodhdMarketDataSource(apiToken, baseUrl, timeout, executor, new DefaultJsonReader());
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
