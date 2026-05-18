package org.hatrack.frauholle.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.engine.Backtester;
import org.hatrack.frauholle.error.InvalidAddToPositionDirectionException;
import org.hatrack.frauholle.error.InvalidBacktestSpecException;
import org.hatrack.frauholle.error.InvalidExplicitFillException;
import org.hatrack.frauholle.error.SignalGenerationException;
import org.hatrack.frauholle.internal.MetricsCalculator;
import org.hatrack.frauholle.model.Direction;
import org.hatrack.frauholle.model.EquityPoint;
import org.hatrack.frauholle.model.Signal;
import org.hatrack.frauholle.model.Trade;
import org.hatrack.frauholle.port.SignalGenerator;
import org.hatrack.frauholle.result.BacktestMetrics;
import org.hatrack.frauholle.result.BacktestResult;
import org.hatrack.frauholle.spec.BacktestSpec;
import org.hatrack.frauholle.spec.BacktestSpecBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrauHolleStepDefinitions {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final Signal HOLD = new Signal.Hold();

    private BacktestSpecBuilder builder;
    private List<OHLCBar> series;
    private Map<Integer, Signal> schedule;
    private SignalGenerator scheduledGenerator;
    private BacktestSpec spec;
    private BacktestResult result;
    private BacktestResult result2;
    private List<EquityPoint> equityCurve;
    private List<Trade> tradeList = new ArrayList<>();
    private BigDecimal periodsPerYear = new BigDecimal("252");
    private BacktestMetrics metrics;
    private Exception thrown;

    // --- given: builder, series, strategy ---

    @Given("a backtest builder")
    public void aBacktestBuilder() {
        builder = BacktestSpec.builder();
        schedule = new HashMap<>();
        scheduledGenerator = context -> schedule.getOrDefault(context.barIndex(), HOLD);
        series = null;
        spec = null;
        result = null;
        metrics = null;
        thrown = null;
    }

    @Given("an empty OHLC series")
    public void anEmptyOhlcSeries() {
        series = List.of();
        builder.withSeries(series);
    }

    @Given("an OHLC series of {int} daily bars")
    public void anOhlcSeriesOfDailyBars(int n) {
        series = dailyBars(n);
        builder.withSeries(series);
    }

    @Given("an OHLC series with irregular spacing")
    public void anOhlcSeriesWithIrregularSpacing() {
        List<OHLCBar> bars = new ArrayList<>();
        long[] offsetsSeconds = {0, 86400, 86400 + 43200, 4 * 86400};
        for (int i = 0; i < offsetsSeconds.length; i++) {
            BigDecimal base = new BigDecimal(100 + i);
            bars.add(new OHLCBar(BASE.plusSeconds(offsetsSeconds[i]),
                    base, base.add(TWO), base.subtract(TWO), base.add(BigDecimal.ONE),
                    java.util.Optional.empty()));
        }
        series = bars;
        builder.withSeries(series);
    }

    @Given("an OHLC series with an OHLC invariant violation")
    public void anOhlcSeriesWithAnOhlcInvariantViolation() {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BigDecimal v = new BigDecimal(100 + i);
            if (i == 2) {
                // high (1) < low (200) violates the OHLC invariants
                bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                        v, BigDecimal.ONE, new BigDecimal("200"), v, java.util.Optional.empty()));
            } else {
                bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                        v, v.add(TWO), v.subtract(TWO), v.add(BigDecimal.ONE), java.util.Optional.empty()));
            }
        }
        series = bars;
        builder.withSeries(series);
    }

    @Given("an OHLC series with bars out of chronological order")
    public void anOhlcSeriesWithBarsOutOfOrder() {
        List<OHLCBar> bars = dailyBars(5);
        // swap bars 2 and 3 so the series is no longer ascending by time
        OHLCBar b2 = bars.get(2);
        OHLCBar b3 = bars.get(3);
        bars.set(2, new OHLCBar(b3.time(), b2.open(), b2.high(), b2.low(), b2.close(), b2.volume()));
        bars.set(3, new OHLCBar(b2.time(), b3.open(), b3.high(), b3.low(), b3.close(), b3.volume()));
        series = bars;
        builder.withSeries(series);
    }

    @Given("an OHLC series with a duplicate timestamp")
    public void anOhlcSeriesWithADuplicateTimestamp() {
        List<OHLCBar> bars = dailyBars(5);
        OHLCBar b3 = bars.get(3);
        bars.set(3, new OHLCBar(bars.get(2).time(),
                b3.open(), b3.high(), b3.low(), b3.close(), b3.volume()));
        series = bars;
        builder.withSeries(series);
    }

    @Given("an OHLC series with a bar whose open exceeds its high")
    public void anOhlcSeriesWithOpenExceedingHigh() {
        seriesWithViolatingBar(new OHLCBar(BASE.plusSeconds(2 * 86400L),
                new BigDecimal("200"), new BigDecimal("102"), new BigDecimal("98"),
                new BigDecimal("100"), java.util.Optional.empty()));
    }

    @Given("an OHLC series with a bar whose close is below its low")
    public void anOhlcSeriesWithCloseBelowLow() {
        seriesWithViolatingBar(new OHLCBar(BASE.plusSeconds(2 * 86400L),
                new BigDecimal("100"), new BigDecimal("102"), new BigDecimal("98"),
                new BigDecimal("50"), java.util.Optional.empty()));
    }

    @Given("an OHLC series with a bar whose volume is negative")
    public void anOhlcSeriesWithNegativeVolume() {
        seriesWithViolatingBar(new OHLCBar(BASE.plusSeconds(2 * 86400L),
                new BigDecimal("100"), new BigDecimal("102"), new BigDecimal("98"),
                new BigDecimal("100"), java.util.Optional.of(new BigDecimal("-1"))));
    }

    private void seriesWithViolatingBar(OHLCBar violating) {
        List<OHLCBar> bars = dailyBars(5);
        bars.set(2, violating);
        series = bars;
        builder.withSeries(series);
    }

    @Given("initial cash {bigdecimal}")
    public void initialCash(BigDecimal cash) {
        builder.withInitialCash(cash);
    }

    @Given("the strategy holds every bar")
    public void theStrategyHoldsEveryBar() {
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy buys {int} at bar {int}")
    public void theStrategyBuysAtBar(int quantity, int bar) {
        schedule.put(bar, new Signal.Buy(new BigDecimal(quantity)));
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy sells {int} at bar {int}")
    public void theStrategySellsAtBar(int quantity, int bar) {
        schedule.put(bar, new Signal.Sell(new BigDecimal(quantity)));
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy closes the position at bar {int}")
    public void theStrategyClosesThePositionAtBar(int bar) {
        schedule.put(bar, new Signal.ClosePosition());
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy closes at price {bigdecimal} as of bar {int} at bar {int}")
    public void theStrategyClosesAtPrice(BigDecimal price, int fillBar, int signalBar) {
        schedule.put(signalBar, new Signal.ClosePositionAtPrice(price, series.get(fillBar).time()));
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy closes at price {bigdecimal} intrabar after bar {int}")
    public void theStrategyClosesIntrabarAfterBar(BigDecimal price, int signalBar) {
        Instant fillTime = series.get(signalBar).time().plusSeconds(43200);
        schedule.put(signalBar, new Signal.ClosePositionAtPrice(price, fillTime));
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy adds {int} {word} at bar {int}")
    public void theStrategyAddsAtBar(int quantity, String direction, int bar) {
        schedule.put(bar, new Signal.AddToPosition(new BigDecimal(quantity),
                Direction.valueOf(direction)));
        builder.withSignalGenerator(scheduledGenerator);
    }

    @Given("the strategy throws at bar {int}")
    public void theStrategyThrowsAtBar(int bar) {
        builder.withSignalGenerator(context -> {
            if (context.barIndex() == bar) {
                throw new IllegalStateException("strategy failure at bar " + bar);
            }
            return HOLD;
        });
    }

    @Given("the strategy returns a null signal at bar {int}")
    public void theStrategyReturnsANullSignalAtBar(int bar) {
        builder.withSignalGenerator(context -> context.barIndex() == bar ? null : HOLD);
    }

    // --- given: metrics inputs ---

    @Given("an equity curve {}")
    public void anEquityCurve(String csv) {
        equityCurve = new ArrayList<>();
        String[] tokens = csv.split(",");
        for (int i = 0; i < tokens.length; i++) {
            BigDecimal equity = new BigDecimal(tokens[i].trim());
            equityCurve.add(new EquityPoint(BASE.plusSeconds(i * 86400L),
                    equity, equity, BigDecimal.ZERO));
        }
    }

    @Given("trades with pnls {}")
    public void tradesWithPnls(String csv) {
        tradeList = new ArrayList<>();
        for (String token : csv.split(",")) {
            BigDecimal pnl = new BigDecimal(token.trim());
            tradeList.add(new Trade(Direction.LONG, BigDecimal.ONE, BASE, BigDecimal.ONE,
                    BASE, BigDecimal.ONE, pnl, BigDecimal.ZERO));
        }
    }

    @Given("periodsPerYear is {int}")
    public void periodsPerYearIs(int value) {
        periodsPerYear = new BigDecimal(value);
    }

    // --- when ---

    @When("I build the backtest spec")
    public void iBuildTheBacktestSpec() {
        thrown = null;
        try {
            spec = builder.build();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I run the backtest")
    public void iRunTheBacktest() {
        thrown = null;
        try {
            spec = builder.build();
            result = new Backtester().run(spec);
            metrics = result.metrics();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I run the backtest with a null spec")
    public void iRunTheBacktestWithANullSpec() {
        thrown = null;
        try {
            new Backtester().run(null);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I run the backtest twice")
    public void iRunTheBacktestTwice() throws Exception {
        spec = builder.build();
        result = new Backtester().run(spec);
        result2 = new Backtester().run(spec);
    }

    @When("I compute the metrics")
    public void iComputeTheMetrics() {
        metrics = MetricsCalculator.compute(equityCurve, tradeList, periodsPerYear);
    }

    // --- then ---

    @Then("an InvalidBacktestSpecException is thrown with violatedRule {string}")
    public void invalidSpecWithViolatedRule(String rule) {
        assertTrue(thrown instanceof InvalidBacktestSpecException,
                "expected InvalidBacktestSpecException but was " + thrown);
        String actual = ((InvalidBacktestSpecException) thrown).violatedRule();
        assertTrue(rule.equals(actual), "violatedRule: expected " + rule + " but was " + actual);
    }

    @Then("the backtest spec builds successfully")
    public void theBacktestSpecBuildsSuccessfully() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(spec != null, "spec was not built");
    }

    @Then("the backtest result is not null")
    public void theBacktestResultIsNotNull() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(result != null && result.metrics() != null, "result or metrics is null");
    }

    @Then("an open position remains at the end")
    public void anOpenPositionRemainsAtTheEnd() {
        requireResult();
        assertTrue(result.openPositionAtEnd().isPresent(), "expected an open position");
    }

    @Then("no open position remains at the end")
    public void noOpenPositionRemainsAtTheEnd() {
        requireResult();
        assertTrue(result.openPositionAtEnd().isEmpty(), "expected no open position");
    }

    @Then("the open position quantity is {bigdecimal}")
    public void theOpenPositionQuantityIs(BigDecimal quantity) {
        requireResult();
        assertEquals(quantity, result.openPositionAtEnd().orElseThrow().quantity(), "quantity");
    }

    @Then("the open position entryPrice is {bigdecimal}")
    public void theOpenPositionEntryPriceIs(BigDecimal price) {
        requireResult();
        assertEquals(price, result.openPositionAtEnd().orElseThrow().entryPrice(), "entryPrice");
    }

    @Then("the open position entryTime is bar {int}")
    public void theOpenPositionEntryTimeIsBar(int barIndex) {
        requireResult();
        assertTrue(result.openPositionAtEnd().orElseThrow().entryTime().equals(series.get(barIndex).time()),
                "entryTime mismatch");
    }

    @Then("the result has {int} trade(s)")
    public void theResultHasTrades(int count) {
        requireResult();
        assertTrue(result.trades().size() == count,
                "trade count: expected " + count + " but was " + result.trades().size());
    }

    @Then("trade {int} exitPrice is {bigdecimal}")
    public void tradeExitPriceIs(int index, BigDecimal price) {
        requireResult();
        assertEquals(price, result.trades().get(index).exitPrice(), "exitPrice");
    }

    @Then("trade {int} exitTime is bar {int}")
    public void tradeExitTimeIsBar(int index, int barIndex) {
        requireResult();
        assertTrue(result.trades().get(index).exitTime().equals(series.get(barIndex).time()),
                "exitTime mismatch");
    }

    @Then("trade {int} exitTime is intrabar after bar {int}")
    public void tradeExitTimeIsIntrabarAfterBar(int index, int barIndex) {
        requireResult();
        Instant expected = series.get(barIndex).time().plusSeconds(43200);
        Instant actual = result.trades().get(index).exitTime();
        assertTrue(actual.equals(expected),
                "trade " + index + " exitTime: expected intrabar instant " + expected
                        + " but was " + actual);
    }

    @Then("diagnostics {word} is {int}")
    public void diagnosticsIs(String field, int expected) {
        requireResult();
        int actual = switch (field) {
            case "ignoredBuySignals" -> result.diagnostics().ignoredBuySignals();
            case "ignoredSellSignals" -> result.diagnostics().ignoredSellSignals();
            case "noOpClosePositionSignals" -> result.diagnostics().noOpClosePositionSignals();
            case "unfilledSignalsAtEndOfSeries" -> result.diagnostics().unfilledSignalsAtEndOfSeries();
            case "forcedClosesAtExplicitPrice" -> result.diagnostics().forcedClosesAtExplicitPrice();
            case "addToPositionCount" -> result.diagnostics().addToPositionCount();
            case "addToPositionOnNoPositionCount" ->
                    result.diagnostics().addToPositionOnNoPositionCount();
            default -> throw new IllegalArgumentException("unknown diagnostic: " + field);
        };
        assertTrue(actual == expected, field + ": expected " + expected + " but was " + actual);
    }

    @Then("the equity curve has {int} points")
    public void theEquityCurveHasPoints(int count) {
        requireResult();
        assertTrue(result.equityCurve().size() == count,
                "equity curve size: expected " + count + " but was " + result.equityCurve().size());
    }

    @Then("the first equity point equity is {bigdecimal}")
    public void theFirstEquityPointEquityIs(BigDecimal equity) {
        requireResult();
        assertEquals(equity, result.equityCurve().get(0).equity(), "first equity");
    }

    @Then("the last equity point equity is {bigdecimal}")
    public void theLastEquityPointEquityIs(BigDecimal equity) {
        requireResult();
        List<EquityPoint> curve = result.equityCurve();
        assertEquals(equity, curve.get(curve.size() - 1).equity(), "last equity");
    }

    @Then("a NullPointerException is thrown")
    public void aNullPointerExceptionIsThrown() {
        assertTrue(thrown instanceof NullPointerException,
                "expected NullPointerException but was " + thrown);
    }

    @Then("a SignalGenerationException is thrown")
    public void aSignalGenerationExceptionIsThrown() {
        assertTrue(thrown instanceof SignalGenerationException,
                "expected SignalGenerationException but was " + thrown);
    }

    @Then("an InvalidExplicitFillException is thrown")
    public void anInvalidExplicitFillExceptionIsThrown() {
        assertTrue(thrown instanceof InvalidExplicitFillException,
                "expected InvalidExplicitFillException but was " + thrown);
    }

    @Then("the exception fillTime is bar {int}")
    public void theExceptionFillTimeIsBar(int barIndex) {
        InvalidExplicitFillException e = (InvalidExplicitFillException) thrown;
        assertTrue(e.fillTime().equals(series.get(barIndex).time()),
                "exception fillTime: expected bar " + barIndex + " but was " + e.fillTime());
    }

    @Then("the exception barTime is bar {int}")
    public void theExceptionBarTimeIsBar(int barIndex) {
        InvalidExplicitFillException e = (InvalidExplicitFillException) thrown;
        assertTrue(e.barTime().equals(series.get(barIndex).time()),
                "exception barTime: expected bar " + barIndex + " but was " + e.barTime());
    }

    @Then("the exception barIndex is {int}")
    public void theExceptionBarIndexIs(int barIndex) {
        int actual = ((SignalGenerationException) thrown).barIndex();
        assertTrue(actual == barIndex, "barIndex: expected " + barIndex + " but was " + actual);
    }

    @Then("an InvalidAddToPositionDirectionException is thrown")
    public void anInvalidAddToPositionDirectionExceptionIsThrown() {
        assertTrue(thrown instanceof InvalidAddToPositionDirectionException,
                "expected InvalidAddToPositionDirectionException but was " + thrown);
    }

    @Then("the AddToPosition exception barIndex is {int}")
    public void theAddToPositionExceptionBarIndexIs(int barIndex) {
        var e = (InvalidAddToPositionDirectionException) thrown;
        assertTrue(e.barIndex() == barIndex,
                "barIndex: expected " + barIndex + " but was " + e.barIndex());
    }

    @Then("the AddToPosition exception barTime is bar {int}")
    public void theAddToPositionExceptionBarTimeIsBar(int barIndex) {
        var e = (InvalidAddToPositionDirectionException) thrown;
        assertTrue(e.barTime().equals(series.get(barIndex).time()),
                "barTime: expected bar " + barIndex + " but was " + e.barTime());
    }

    @Then("the AddToPosition exception openPositionDirection is {word}")
    public void theAddToPositionExceptionOpenPositionDirectionIs(String direction) {
        var e = (InvalidAddToPositionDirectionException) thrown;
        assertTrue(e.openPositionDirection() == Direction.valueOf(direction),
                "openPositionDirection: expected " + direction
                        + " but was " + e.openPositionDirection());
    }

    @Then("the AddToPosition exception signalDirection is {word}")
    public void theAddToPositionExceptionSignalDirectionIs(String direction) {
        var e = (InvalidAddToPositionDirectionException) thrown;
        assertTrue(e.signalDirection() == Direction.valueOf(direction),
                "signalDirection: expected " + direction + " but was " + e.signalDirection());
    }

    @Then("both backtest results are equal")
    public void bothBacktestResultsAreEqual() {
        assertTrue(result.equals(result2), "backtest results are not equal");
    }

    @Then("metrics {word} is {bigdecimal}")
    public void metricsIs(String name, BigDecimal expected) {
        assertEquals(expected, metricValue(name), name);
    }

    @Then("metrics {word} is approximately {bigdecimal}")
    public void metricsIsApproximately(String name, BigDecimal expected) {
        BigDecimal actual = metricValue(name);
        assertTrue(actual.subtract(expected).abs().compareTo(new BigDecimal("0.001")) < 0,
                name + ": expected ~" + expected + " but was " + actual);
    }

    // --- helpers ---

    private static final BigDecimal TWO = new BigDecimal("2");

    private BigDecimal metricValue(String name) {
        return switch (name) {
            case "totalReturn" -> metrics.totalReturn();
            case "winRate" -> metrics.winRate();
            case "numTrades" -> new BigDecimal(metrics.numTrades());
            case "maxDrawdown" -> metrics.maxDrawdown();
            case "sharpeRatio" -> metrics.sharpeRatio();
            case "sortinoRatio" -> metrics.sortinoRatio();
            case "profitFactor" -> metrics.profitFactor();
            case "avgWin" -> metrics.avgWin();
            case "avgLoss" -> metrics.avgLoss();
            case "calmarRatio" -> metrics.calmarRatio();
            default -> throw new IllegalArgumentException("unknown metric: " + name);
        };
    }

    private static List<OHLCBar> dailyBars(int n) {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal open = new BigDecimal(100 + i);
            bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    open, open.add(TWO), open.subtract(TWO), open.add(BigDecimal.ONE),
                    java.util.Optional.of(new BigDecimal(1000 + i))));
        }
        return bars;
    }

    private void requireResult() {
        if (thrown != null) {
            throw new AssertionError("backtest failed unexpectedly: " + thrown, thrown);
        }
        assertTrue(result != null, "no backtest result available");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(BigDecimal expected, BigDecimal actual, String label) {
        if (actual == null || expected.compareTo(actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
