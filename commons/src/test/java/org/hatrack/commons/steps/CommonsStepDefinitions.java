package org.hatrack.commons.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.HeikinAshiCalculator;
import org.hatrack.commons.OHLCAggregator;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCInvariantViolationException;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotLevel;
import org.hatrack.commons.PivotLevels;
import org.hatrack.commons.PivotPointVariant;
import org.hatrack.commons.PivotPoints;
import org.hatrack.commons.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommonsStepDefinitions {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private OHLCBar ohlc;
    private List<OHLCBar> ohlcs;
    private Optional<HABar> previousHa = Optional.empty();
    private HABar computedHa;
    private List<HABar> computedChain;
    private Timeframe timeframe;
    private List<OHLCBar> mutableOhlcList;
    private List<HABar> mutableHaList;
    private OHLCSeries ohlcSeries;
    private HASeries haSeries;
    private PivotLevels pivotLevels;
    private OHLCSeries aggregatedSeries;
    private Exception thrown;

    // --- Heikin Ashi ---

    @Given("an OHLC bar with open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal} at time {string}")
    public void ohlcBarAtTime(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, String time) {
        ohlc = new OHLCBar(Instant.parse(time), o, h, l, c, Optional.empty());
    }

    @Given("no previous HA bar")
    public void noPreviousHaBar() {
        previousHa = Optional.empty();
    }

    @Given("a previous HA bar with haOpen={bigdecimal}, haHigh={bigdecimal}, haLow={bigdecimal}, haClose={bigdecimal}")
    public void previousHaBar(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c) {
        previousHa = Optional.of(new HABar(T0, o, h, l, c));
    }

    @Given("the following OHLC bars:")
    public void theFollowingOhlcBars(DataTable table) {
        ohlcs = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            ohlcs.add(new OHLCBar(
                    Instant.parse(row.get("time")),
                    new BigDecimal(row.get("open")),
                    new BigDecimal(row.get("high")),
                    new BigDecimal(row.get("low")),
                    new BigDecimal(row.get("close")),
                    Optional.empty()));
        }
    }

    @When("I compute the HA bar")
    public void iComputeTheHaBar() {
        capture(() -> computedHa = HeikinAshiCalculator.compute(previousHa, ohlc));
    }

    @When("I compute the HA chain")
    public void iComputeTheHaChain() {
        capture(() -> computedChain = HeikinAshiCalculator.computeChain(previousHa, ohlcs));
    }

    @Then("haClose is {bigdecimal}")
    public void haCloseIs(BigDecimal expected) {
        assertNumericEquals(expected, computedHa.haClose(), "haClose");
    }

    @Then("haOpen is {bigdecimal}")
    public void haOpenIs(BigDecimal expected) {
        assertNumericEquals(expected, computedHa.haOpen(), "haOpen");
    }

    @Then("haHigh is {bigdecimal}")
    public void haHighIs(BigDecimal expected) {
        assertNumericEquals(expected, computedHa.haHigh(), "haHigh");
    }

    @Then("haLow is {bigdecimal}")
    public void haLowIs(BigDecimal expected) {
        assertNumericEquals(expected, computedHa.haLow(), "haLow");
    }

    @Then("the HA bar time is {string}")
    public void theHaBarTimeIs(String time) {
        assertTrue(Instant.parse(time).equals(computedHa.time()),
                "ha bar time: expected " + time + " but was " + computedHa.time());
    }

    @Then("the HA chain has {int} bars")
    public void theHaChainHasBars(int n) {
        assertTrue(computedChain.size() == n,
                "ha chain size: expected " + n + " but was " + computedChain.size());
    }

    @Then("the HA chain bars are:")
    public void theHaChainBarsAre(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        assertTrue(rows.size() == computedChain.size(),
                "ha chain size: expected " + rows.size() + " but was " + computedChain.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            HABar bar = computedChain.get(i);
            assertNumericEquals(new BigDecimal(r.get("haOpen")), bar.haOpen(), "haOpen row " + i);
            assertNumericEquals(new BigDecimal(r.get("haHigh")), bar.haHigh(), "haHigh row " + i);
            assertNumericEquals(new BigDecimal(r.get("haLow")), bar.haLow(), "haLow row " + i);
            assertNumericEquals(new BigDecimal(r.get("haClose")), bar.haClose(), "haClose row " + i);
        }
    }

    // --- OHLC invariants ---

    @Given("an OHLC bar with open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal}")
    public void ohlcBarNoTime(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c) {
        ohlc = new OHLCBar(T0, o, h, l, c, Optional.empty());
    }

    @Given("an OHLC bar with open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal} and volume={bigdecimal}")
    public void ohlcBarWithVolume(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, BigDecimal v) {
        ohlc = new OHLCBar(T0, o, h, l, c, Optional.of(v));
    }

    @Given("an OHLC bar with open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal} and no volume")
    public void ohlcBarNoVolume(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c) {
        ohlc = new OHLCBar(T0, o, h, l, c, Optional.empty());
    }

    @When("I validate invariants")
    public void iValidateInvariants() {
        capture(() -> ohlc.validateInvariants());
    }

    @When("I construct an OHLC bar with open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal}")
    public void iConstructOhlcBar(BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c) {
        capture(() -> ohlc = new OHLCBar(T0, o, h, l, c, Optional.empty()));
    }

    @When("I construct an OHLC bar with a null close")
    public void iConstructOhlcBarWithNullClose() {
        capture(() -> ohlc = new OHLCBar(T0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                null, Optional.empty()));
    }

    @Then("an OHLCInvariantViolationException is thrown")
    public void anOhlcInvariantViolationExceptionIsThrown() {
        assertTrue(thrown instanceof OHLCInvariantViolationException,
                "expected OHLCInvariantViolationException but was " + thrown);
    }

    @Then("the violated invariant is {string}")
    public void theViolatedInvariantIs(String name) {
        OHLCInvariantViolationException e = (OHLCInvariantViolationException) thrown;
        assertTrue(name.equals(e.violatedInvariant()),
                "violated invariant: expected " + name + " but was " + e.violatedInvariant());
    }

    @Then("the OHLC bar is constructed without exception")
    public void theOhlcBarIsConstructedWithoutException() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(ohlc != null, "OHLC bar was not constructed");
    }

    @Then("a NullPointerException is thrown")
    public void aNullPointerExceptionIsThrown() {
        assertTrue(thrown instanceof NullPointerException,
                "expected NullPointerException but was " + thrown);
    }

    // --- Timeframe ---

    @When("I parse the timeframe wire string {string}")
    public void iParseTheTimeframeWireString(String wire) {
        capture(() -> timeframe = Timeframe.fromWire(wire));
    }

    @When("I parse a null timeframe wire string")
    public void iParseANullTimeframeWireString() {
        capture(() -> timeframe = Timeframe.fromWire(null));
    }

    @When("I parse a blank timeframe wire string")
    public void iParseABlankTimeframeWireString() {
        capture(() -> timeframe = Timeframe.fromWire("   "));
    }

    @When("I parse a whitespace-padded timeframe wire string")
    public void iParseAWhitespacePaddedTimeframeWireString() {
        capture(() -> timeframe = Timeframe.fromWire("  1d  "));
    }

    @Then("re-serializing it returns {string}")
    public void reSerializingItReturns(String wire) {
        assertTrue(wire.equals(timeframe.wire()),
                "wire: expected " + wire + " but was " + timeframe.wire());
    }

    @Then("the timeframe unit is {word}")
    public void theTimeframeUnitIs(String unit) {
        assertTrue(unit.equals(timeframe.unit().name()),
                "timeframe unit: expected " + unit + " but was " + timeframe.unit());
    }

    @Then("an IllegalArgumentException is thrown")
    public void anIllegalArgumentExceptionIsThrown() {
        assertTrue(thrown instanceof IllegalArgumentException,
                "expected IllegalArgumentException but was " + thrown);
    }

    // --- Series ---

    @Given("a mutable list of {int} OHLC bars")
    public void aMutableListOfOhlcBars(int n) {
        mutableOhlcList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            mutableOhlcList.add(new OHLCBar(T0.plusSeconds(i * 86400L),
                    BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, Optional.empty()));
        }
    }

    @Given("a mutable list of {int} HA bars")
    public void aMutableListOfHaBars(int n) {
        mutableHaList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            mutableHaList.add(new HABar(T0.plusSeconds(i * 86400L),
                    BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN));
        }
    }

    @Given("an OHLCSeries built from that list")
    public void anOhlcSeriesBuiltFromThatList() {
        ohlcSeries = new OHLCSeries(mutableOhlcList);
    }

    @Given("an HASeries built from that list")
    public void anHaSeriesBuiltFromThatList() {
        haSeries = new HASeries(mutableHaList);
    }

    @When("I clear the original list")
    public void iClearTheOriginalList() {
        if (mutableOhlcList != null) {
            mutableOhlcList.clear();
        }
        if (mutableHaList != null) {
            mutableHaList.clear();
        }
    }

    @When("I construct an OHLCSeries with a null bar list")
    public void iConstructAnOhlcSeriesWithANullBarList() {
        capture(() -> ohlcSeries = new OHLCSeries(null));
    }

    @When("I construct an HASeries with a null bar list")
    public void iConstructAnHaSeriesWithANullBarList() {
        capture(() -> haSeries = new HASeries(null));
    }

    @When("I construct an OHLCSeries from a list containing a null bar")
    public void iConstructAnOhlcSeriesFromAListContainingANullBar() {
        List<OHLCBar> list = new ArrayList<>();
        list.add(new OHLCBar(T0, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, Optional.empty()));
        list.add(null);
        capture(() -> ohlcSeries = new OHLCSeries(list));
    }

    @When("I construct an HASeries from a list containing a null bar")
    public void iConstructAnHaSeriesFromAListContainingANullBar() {
        List<HABar> list = new ArrayList<>();
        list.add(new HABar(T0, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN));
        list.add(null);
        capture(() -> haSeries = new HASeries(list));
    }

    @Then("the OHLCSeries still has {int} bars")
    public void theOhlcSeriesStillHasBars(int n) {
        assertTrue(ohlcSeries.bars().size() == n,
                "OHLCSeries bars: expected " + n + " but was " + ohlcSeries.bars().size());
    }

    @Then("the HASeries still has {int} bars")
    public void theHaSeriesStillHasBars(int n) {
        assertTrue(haSeries.bars().size() == n,
                "HASeries bars: expected " + n + " but was " + haSeries.bars().size());
    }

    // --- Pivot points ---

    @Given("a previous-period OHLC bar with high={bigdecimal}, low={bigdecimal}, close={bigdecimal}")
    public void aPreviousPeriodOhlcBar(BigDecimal h, BigDecimal l, BigDecimal c) {
        ohlc = new OHLCBar(T0, c, h, l, c, Optional.empty());
    }

    @When("I compute {word} pivots")
    public void iComputePivots(String variant) {
        capture(() -> pivotLevels = PivotPoints.levels(ohlc, PivotPointVariant.valueOf(variant)));
    }

    @Then("pivot {word} is {bigdecimal}")
    public void pivotLevelIs(String level, BigDecimal expected) {
        assertNumericEquals(expected, pivotLevels.value(PivotLevel.valueOf(level)), "pivot " + level);
    }

    @Then("pivot {word} is absent")
    public void pivotLevelIsAbsent(String level) {
        assertTrue(pivotLevels.value(PivotLevel.valueOf(level)) == null,
                "pivot " + level + " expected absent but was " + pivotLevels.value(PivotLevel.valueOf(level)));
    }

    // --- OHLC aggregation ---

    @Given("the following OHLC bars with volume:")
    public void theFollowingOhlcBarsWithVolume(DataTable table) {
        ohlcs = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            String vol = row.get("volume");
            Optional<BigDecimal> volume = (vol == null || vol.isBlank())
                    ? Optional.empty() : Optional.of(new BigDecimal(vol));
            ohlcs.add(new OHLCBar(
                    Instant.parse(row.get("time")),
                    new BigDecimal(row.get("open")),
                    new BigDecimal(row.get("high")),
                    new BigDecimal(row.get("low")),
                    new BigDecimal(row.get("close")),
                    volume));
        }
    }

    @Then("aggregated bar {int} has volume {bigdecimal}")
    public void aggregatedBarHasVolume(int index, BigDecimal expected) {
        Optional<BigDecimal> actual = aggregatedSeries.bars().get(index).volume();
        assertTrue(actual.isPresent(), "bar " + index + " volume: expected present but was empty");
        assertNumericEquals(expected, actual.get(), "bar " + index + " volume");
    }

    @Then("aggregated bar {int} has no volume")
    public void aggregatedBarHasNoVolume(int index) {
        Optional<BigDecimal> actual = aggregatedSeries.bars().get(index).volume();
        assertTrue(actual.isEmpty(), "bar " + index + " volume: expected empty but was " + actual);
    }

    @When("I aggregate to period {string}")
    public void iAggregateToPeriod(String period) {
        capture(() -> aggregatedSeries =
                OHLCAggregator.toPeriod(new OHLCSeries(ohlcs), Timeframe.fromWire(period)));
    }

    @Then("the aggregated series has {int} bars")
    public void theAggregatedSeriesHasBars(int n) {
        assertTrue(aggregatedSeries.bars().size() == n,
                "aggregated bars: expected " + n + " but was " + aggregatedSeries.bars().size());
    }

    @Then("aggregated bar {int} has open={bigdecimal}, high={bigdecimal}, low={bigdecimal}, close={bigdecimal} at time {string}")
    public void aggregatedBarHas(int index, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c,
                                 String time) {
        OHLCBar bar = aggregatedSeries.bars().get(index);
        assertNumericEquals(o, bar.open(), "bar " + index + " open");
        assertNumericEquals(h, bar.high(), "bar " + index + " high");
        assertNumericEquals(l, bar.low(), "bar " + index + " low");
        assertNumericEquals(c, bar.close(), "bar " + index + " close");
        assertTrue(Instant.parse(time).equals(bar.time()),
                "bar " + index + " time: expected " + time + " but was " + bar.time());
    }

    // --- shared ---

    @Then("no exception is thrown")
    public void noExceptionIsThrown() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
    }

    private void capture(Runnable action) {
        thrown = null;
        try {
            action.run();
        } catch (Exception e) {
            thrown = e;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertNumericEquals(BigDecimal expected, BigDecimal actual, String label) {
        if (actual == null || expected.compareTo(actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
