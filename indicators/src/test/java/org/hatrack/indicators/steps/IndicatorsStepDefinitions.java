package org.hatrack.indicators.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.hatrack.indicators.BollingerBands;
import org.hatrack.indicators.Indicators;
import org.hatrack.indicators.MacdResult;
import org.hatrack.indicators.StochasticResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class IndicatorsStepDefinitions {

    private List<BigDecimal> prices;
    private List<BigDecimal> highs;
    private List<BigDecimal> lows;
    private List<BigDecimal> closes;

    private BigDecimal[] series;
    private MacdResult macd;
    private BollingerBands bollinger;
    private StochasticResult stochastic;
    private RuntimeException thrown;

    @Given("the price series {string}")
    public void thePriceSeries(String csv) {
        prices = parse(csv);
    }

    @Given("the OHLC bars:")
    public void theOhlcBars(DataTable table) {
        highs = new ArrayList<>();
        lows = new ArrayList<>();
        closes = new ArrayList<>();
        for (var row : table.asMaps()) {
            highs.add(new BigDecimal(row.get("high").trim()));
            lows.add(new BigDecimal(row.get("low").trim()));
            closes.add(new BigDecimal(row.get("close").trim()));
        }
    }

    @When("I compute SMA with period {int}")
    public void iComputeSma(int period) {
        capture(() -> series = Indicators.sma(prices, period));
    }

    @When("I compute EMA with period {int}")
    public void iComputeEma(int period) {
        capture(() -> series = Indicators.ema(prices, period));
    }

    @When("I compute RSI with period {int}")
    public void iComputeRsi(int period) {
        capture(() -> series = Indicators.rsi(prices, period));
    }

    @When("I compute MACD with fast {int} slow {int} signal {int}")
    public void iComputeMacd(int fast, int slow, int signal) {
        capture(() -> macd = Indicators.macd(prices, fast, slow, signal));
    }

    @When("I compute Bollinger Bands with period {int} and multiplier {int}")
    public void iComputeBollinger(int period, int multiplier) {
        capture(() -> bollinger = Indicators.bollinger(prices, period, new BigDecimal(multiplier)));
    }

    @When("I compute ATR with period {int}")
    public void iComputeAtr(int period) {
        capture(() -> series = Indicators.atr(highs, lows, closes, period));
    }

    @When("I compute Stochastic with kPeriod {int} dPeriod {int} smoothing {int}")
    public void iComputeStochastic(int kPeriod, int dPeriod, int smoothing) {
        capture(() -> stochastic = Indicators.stochastic(highs, lows, closes, kPeriod, dPeriod, smoothing));
    }

    @When("I compute ADX with period {int}")
    public void iComputeAdx(int period) {
        capture(() -> series = Indicators.adx(highs, lows, closes, period));
    }

    @Then("the indicator series equals {string}")
    public void theIndicatorSeriesEquals(String expected) {
        assertSeries(expected, series);
    }

    @Then("the MACD line equals {string}")
    public void theMacdLineEquals(String expected) {
        assertSeries(expected, macd.macdLine());
    }

    @Then("the MACD signal line equals {string}")
    public void theMacdSignalLineEquals(String expected) {
        assertSeries(expected, macd.signalLine());
    }

    @Then("the MACD histogram equals {string}")
    public void theMacdHistogramEquals(String expected) {
        assertSeries(expected, macd.histogram());
    }

    @Then("the Bollinger upper band equals {string}")
    public void theBollingerUpperEquals(String expected) {
        assertSeries(expected, bollinger.upper());
    }

    @Then("the Bollinger middle band equals {string}")
    public void theBollingerMiddleEquals(String expected) {
        assertSeries(expected, bollinger.middle());
    }

    @Then("the Bollinger lower band equals {string}")
    public void theBollingerLowerEquals(String expected) {
        assertSeries(expected, bollinger.lower());
    }

    @Then("the Stochastic %K equals {string}")
    public void theStochasticKEquals(String expected) {
        assertSeries(expected, stochastic.percentK());
    }

    @Then("the Stochastic %D equals {string}")
    public void theStochasticDEquals(String expected) {
        assertSeries(expected, stochastic.percentD());
    }

    @Then("indicator values before index {int} are null")
    public void valuesBeforeIndexAreNull(int index) {
        for (int i = 0; i < index; i++) {
            assertTrue(series[i] == null, "expected null at index " + i + " but got " + series[i]);
        }
    }

    @Then("indicator values from index {int} are present")
    public void valuesFromIndexArePresent(int index) {
        for (int i = index; i < series.length; i++) {
            assertTrue(series[i] != null, "expected a value at index " + i + " but got null");
        }
    }

    @Then("an IllegalArgumentException is thrown")
    public void anIllegalArgumentExceptionIsThrown() {
        assertTrue(thrown != null, "expected an exception to be thrown");
        assertTrue(thrown instanceof IllegalArgumentException,
                "expected IllegalArgumentException, got " + thrown.getClass().getName());
    }

    private void capture(Runnable action) {
        thrown = null;
        try {
            action.run();
        } catch (RuntimeException e) {
            thrown = e;
        }
    }

    // DECIMAL64 carries ~16 significant digits; reference values are compared
    // with an absolute tolerance well above last-digit rounding noise and well
    // below any genuine formula error.
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0000001");

    private static void assertSeries(String expected, BigDecimal[] actual) {
        String[] cells = expected.split(",");
        assertTrue(cells.length == actual.length,
                "series length mismatch: expected " + cells.length + " but was " + actual.length);
        for (int i = 0; i < cells.length; i++) {
            String cell = cells[i].trim();
            if (cell.equals("null")) {
                assertTrue(actual[i] == null, "expected null at index " + i + " but got " + actual[i]);
            } else {
                assertTrue(actual[i] != null, "expected " + cell + " at index " + i + " but got null");
                BigDecimal diff = new BigDecimal(cell).subtract(actual[i]).abs();
                assertTrue(diff.compareTo(TOLERANCE) <= 0,
                        "at index " + i + " expected " + cell + " but got " + actual[i]);
            }
        }
    }

    private static List<BigDecimal> parse(String csv) {
        List<BigDecimal> out = new ArrayList<>();
        for (String token : csv.split(",")) {
            out.add(new BigDecimal(token.trim()));
        }
        return out;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
