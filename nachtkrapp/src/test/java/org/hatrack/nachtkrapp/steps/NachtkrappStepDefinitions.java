package org.hatrack.nachtkrapp.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.detector.DetectionResult;
import org.hatrack.nachtkrapp.detector.PatternDetector;
import org.hatrack.nachtkrapp.detector.RuleBasedPatternDetector;
import org.hatrack.nachtkrapp.error.InvalidDetectionSpecException;
import org.hatrack.nachtkrapp.match.PatternMatch;
import org.hatrack.nachtkrapp.match.PatternMatch.PriceAboveMA;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAColorChangeRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HADojiRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAStrongCandleRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDSignalCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDZeroCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceMACrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceVsMARule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSILevel50CrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSIThresholdRule;
import org.hatrack.nachtkrapp.rule.MAType;
import org.hatrack.nachtkrapp.spec.DetectionSpec;
import org.hatrack.nachtkrapp.spec.DetectionSpecBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class NachtkrappStepDefinitions {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    private DetectionSpecBuilder builder;
    private final List<DetectionRule> addedRules = new ArrayList<>();
    private List<OHLCBar> ohlcBars;
    private Timeframe timeframeTag;
    private DetectionSpec spec;
    private DetectionResult result;
    private DetectionResult result2;
    private List<DetectionResult> concurrentResults;
    private List<PatternMatch> fullMatches;
    private List<PatternMatch> truncatedMatches;
    private Exception thrown;

    // --- builder / series setup ---

    @Given("a detection spec builder")
    public void aDetectionSpecBuilder() {
        builder = DetectionSpec.builder();
        addedRules.clear();
        ohlcBars = null;
        timeframeTag = null;
        spec = null;
        result = null;
        thrown = null;
    }

    @Given("an empty OHLC series")
    public void anEmptyOhlcSeries() {
        ohlcBars = List.of();
        builder.withSeries(new OHLCSeries(ohlcBars));
    }

    @Given("an OHLC series:")
    public void anOhlcSeriesTable(DataTable table) {
        List<OHLCBar> bars = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            bars.add(new OHLCBar(Instant.parse(row.get("time")),
                    new BigDecimal(row.get("open")), new BigDecimal(row.get("high")),
                    new BigDecimal(row.get("low")), new BigDecimal(row.get("close")),
                    Optional.empty()));
        }
        ohlcBars = bars;
        builder.withSeries(new OHLCSeries(bars));
    }

    @Given("an HA series:")
    public void anHaSeriesTable(DataTable table) {
        List<HABar> bars = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            bars.add(new HABar(Instant.parse(row.get("time")),
                    new BigDecimal(row.get("haOpen")), new BigDecimal(row.get("haHigh")),
                    new BigDecimal(row.get("haLow")), new BigDecimal(row.get("haClose"))));
        }
        builder.withSeries(new HASeries(bars));
    }

    @Given("an OHLC series with closes {}")
    public void anOhlcSeriesWithCloses(String csv) {
        List<BigDecimal> closes = new ArrayList<>();
        for (String token : csv.split(",")) {
            closes.add(new BigDecimal(token.trim()));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series of {int} bars with close strictly increasing")
    public void anOhlcSeriesIncreasing(int n) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            closes.add(new BigDecimal(100 + i));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series of {int} bars with close strictly decreasing")
    public void anOhlcSeriesDecreasing(int n) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            closes.add(new BigDecimal(500 - i));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series rising for {int} bars then falling for {int} bars")
    public void anOhlcSeriesRisingThenFalling(int up, int down) {
        List<BigDecimal> closes = new ArrayList<>();
        int value = 100;
        for (int i = 0; i < up; i++) {
            closes.add(new BigDecimal(value++));
        }
        value -= 2;
        for (int i = 0; i < down; i++) {
            closes.add(new BigDecimal(value--));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series falling for {int} bars then rising for {int} bars")
    public void anOhlcSeriesFallingThenRising(int down, int up) {
        List<BigDecimal> closes = new ArrayList<>();
        int value = 500;
        for (int i = 0; i < down; i++) {
            closes.add(new BigDecimal(value--));
        }
        value += 2;
        for (int i = 0; i < up; i++) {
            closes.add(new BigDecimal(value++));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series accelerating down for {int} bars then up for {int} bars")
    public void anOhlcSeriesAcceleratingDownThenUp(int down, int up) {
        List<BigDecimal> closes = new ArrayList<>();
        long value = 1_000_000L;
        closes.add(BigDecimal.valueOf(value));
        for (int i = 1; i <= down; i++) {
            value -= i;
            closes.add(BigDecimal.valueOf(value));
        }
        for (int i = 1; i <= up; i++) {
            value += i;
            closes.add(BigDecimal.valueOf(value));
        }
        setOhlcSeries(closes);
    }

    @Given("an OHLC series accelerating up for {int} bars then down for {int} bars")
    public void anOhlcSeriesAcceleratingUpThenDown(int up, int down) {
        List<BigDecimal> closes = new ArrayList<>();
        long value = 1_000_000L;
        closes.add(BigDecimal.valueOf(value));
        for (int i = 1; i <= up; i++) {
            value += i;
            closes.add(BigDecimal.valueOf(value));
        }
        for (int i = 1; i <= down; i++) {
            value -= i;
            closes.add(BigDecimal.valueOf(value));
        }
        setOhlcSeries(closes);
    }

    @Given("an HA series of {int} bars with haClose strictly increasing")
    public void anHaSeriesIncreasing(int n) {
        List<HABar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal v = new BigDecimal(100 + i);
            bars.add(new HABar(timeOf(i), v, v, v, v));
        }
        builder.withSeries(new HASeries(bars));
    }

    @Given("an OHLC series with an OHLC invariant violation")
    public void anOhlcSeriesWithAnOhlcInvariantViolation() {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BigDecimal v = new BigDecimal(100 + i);
            if (i == 2) {
                // high (1) < low (200) violates the OHLC invariants
                bars.add(new OHLCBar(timeOf(i), v, BigDecimal.ONE, new BigDecimal("200"), v,
                        Optional.empty()));
            } else {
                bars.add(new OHLCBar(timeOf(i), v, v, v, v, Optional.empty()));
            }
        }
        ohlcBars = bars;
        builder.withSeries(new OHLCSeries(bars));
    }

    @Given("the timeframe tag {string}")
    public void theTimeframeTag(String wire) {
        timeframeTag = Timeframe.fromWire(wire);
        builder.withTimeframe(timeframeTag);
    }

    // --- rules ---

    @Given("the rule HAColorChangeRule with minStreakLength {int}")
    public void ruleHaColorChange(int minStreakLength) {
        addRule(new HAColorChangeRule(minStreakLength));
    }

    @Given("the rule HAStrongCandleRule with wickTolerance {bigdecimal} and minBodyRatio {bigdecimal}")
    public void ruleHaStrongCandle(BigDecimal wickTolerance, BigDecimal minBodyRatio) {
        addRule(new HAStrongCandleRule(wickTolerance, minBodyRatio));
    }

    @Given("the rule HADojiRule with maxBodyRatio {bigdecimal}")
    public void ruleHaDoji(BigDecimal maxBodyRatio) {
        addRule(new HADojiRule(maxBodyRatio));
    }

    @Given("the rule PriceVsMARule with {word} period {int} source {word}")
    public void rulePriceVsMA(String maType, int period, String source) {
        addRule(new PriceVsMARule(MAType.valueOf(maType), period, PriceSource.valueOf(source)));
    }

    @Given("the rule PriceMACrossRule with {word} period {int} source {word}")
    public void rulePriceMACross(String maType, int period, String source) {
        addRule(new PriceMACrossRule(MAType.valueOf(maType), period, PriceSource.valueOf(source)));
    }

    @Given("the rule RSIThresholdRule with period {int} overbought {bigdecimal} oversold {bigdecimal} source {word}")
    public void ruleRsiThreshold(int period, BigDecimal overbought, BigDecimal oversold, String source) {
        addRule(new RSIThresholdRule(period, overbought, oversold, PriceSource.valueOf(source)));
    }

    @Given("the rule RSILevel50CrossRule with period {int} source {word}")
    public void ruleRsiLevel50Cross(int period, String source) {
        addRule(new RSILevel50CrossRule(period, PriceSource.valueOf(source)));
    }

    @Given("the rule MACDSignalCrossRule with fast {int} slow {int} signal {int} source {word}")
    public void ruleMacdSignalCross(int fast, int slow, int signal, String source) {
        addRule(new MACDSignalCrossRule(fast, slow, signal, PriceSource.valueOf(source)));
    }

    @Given("the rule MACDZeroCrossRule with fast {int} slow {int} signal {int} source {word}")
    public void ruleMacdZeroCross(int fast, int slow, int signal, String source) {
        addRule(new MACDZeroCrossRule(fast, slow, signal, PriceSource.valueOf(source)));
    }

    // --- actions ---

    @When("I build the detection spec")
    public void iBuildTheDetectionSpec() {
        thrown = null;
        try {
            spec = builder.build();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I detect")
    public void iDetect() {
        thrown = null;
        try {
            spec = builder.build();
            result = new RuleBasedPatternDetector().detect(spec);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I detect with a null spec")
    public void iDetectWithANullSpec() {
        thrown = null;
        try {
            new RuleBasedPatternDetector().detect(null);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I detect twice")
    public void iDetectTwice() throws Exception {
        spec = builder.build();
        PatternDetector detector = new RuleBasedPatternDetector();
        result = detector.detect(spec);
        result2 = detector.detect(spec);
    }

    @When("I detect with two separate detector instances")
    public void iDetectWithTwoInstances() throws Exception {
        spec = builder.build();
        result = new RuleBasedPatternDetector().detect(spec);
        result2 = new RuleBasedPatternDetector().detect(spec);
    }

    @When("I detect concurrently from {int} threads")
    public void iDetectConcurrently(int threads) throws Exception {
        spec = builder.build();
        PatternDetector detector = new RuleBasedPatternDetector();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<DetectionResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<DetectionResult> task = () -> detector.detect(spec);
                futures.add(pool.submit(task));
            }
            concurrentResults = new ArrayList<>();
            for (Future<DetectionResult> future : futures) {
                concurrentResults.add(future.get());
            }
        } finally {
            pool.shutdown();
        }
    }

    @When("I detect on the full series and on the series truncated to {int} bars")
    public void iDetectFullAndTruncated(int n) throws Exception {
        PatternDetector detector = new RuleBasedPatternDetector();
        spec = builder.build();
        fullMatches = detector.detect(spec).matches();
        DetectionSpecBuilder truncatedBuilder = DetectionSpec.builder()
                .withSeries(new OHLCSeries(ohlcBars.subList(0, n)))
                .addAllRules(addedRules);
        if (timeframeTag != null) {
            truncatedBuilder.withTimeframe(timeframeTag);
        }
        truncatedMatches = detector.detect(truncatedBuilder.build()).matches();
    }

    // --- assertions ---

    @Then("an InvalidDetectionSpecException is thrown with violatedRule {string}")
    public void invalidSpecWithViolatedRule(String rule) {
        assertTrue(thrown instanceof InvalidDetectionSpecException,
                "expected InvalidDetectionSpecException but was " + thrown);
        String actual = ((InvalidDetectionSpecException) thrown).violatedRule();
        assertTrue(rule.equals(actual), "violatedRule: expected " + rule + " but was " + actual);
    }

    @Then("the spec builds successfully")
    public void theSpecBuildsSuccessfully() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(spec != null, "spec was not built");
    }

    @Then("the spec timeframe is empty")
    public void theSpecTimeframeIsEmpty() {
        assertTrue(spec.timeframe().isEmpty(), "expected empty timeframe but was " + spec.timeframe());
    }

    @Then("the result contains {int} {word}")
    public void theResultContainsCount(int expected, String type) {
        requireDetected();
        long actual = countOfType(type);
        assertTrue(actual == expected, type + " count: expected " + expected + " but was " + actual);
    }

    @Then("the result contains at least {int} {word}")
    public void theResultContainsAtLeast(int minimum, String type) {
        requireDetected();
        long actual = countOfType(type);
        assertTrue(actual >= minimum, type + " count: expected >= " + minimum + " but was " + actual);
    }

    @Then("every {word} match has flavor {word}")
    public void everyMatchOfTypeHasFlavor(String type, String flavor) {
        requireDetected();
        for (PatternMatch match : result.matches()) {
            if (match.getClass().getSimpleName().equals(type)) {
                assertTrue(match.flavor().name().equals(flavor),
                        type + " flavor: expected " + flavor + " but was " + match.flavor());
            }
        }
    }

    @Then("the match at index {int} has time {string}")
    public void theMatchAtIndexHasTime(int index, String time) {
        requireDetected();
        Instant actual = result.matches().get(index).time();
        assertTrue(actual.equals(Instant.parse(time)),
                "match[" + index + "] time: expected " + time + " but was " + actual);
    }

    @Then("no match occurs before bar {int}")
    public void noMatchOccursBeforeBar(int oneBasedBar) {
        requireDetected();
        Instant cutoff = ohlcBars.get(oneBasedBar - 1).time();
        for (PatternMatch match : result.matches()) {
            assertTrue(!match.time().isBefore(cutoff),
                    "match before bar " + oneBasedBar + ": " + match.time());
        }
    }

    @Then("the PriceAboveMA matches have at least {int} distinct maValue")
    public void priceAboveMaDistinctValues(int minimum) {
        requireDetected();
        Set<BigDecimal> values = new HashSet<>();
        for (PatternMatch match : result.matches()) {
            if (match instanceof PriceAboveMA priceAboveMa) {
                values.add(priceAboveMa.maValue().stripTrailingZeros());
            }
        }
        assertTrue(values.size() >= minimum,
                "distinct maValue: expected >= " + minimum + " but was " + values.size());
    }

    @Then("the matches are ordered ascending by time")
    public void theMatchesAreOrdered() {
        requireDetected();
        List<PatternMatch> matches = result.matches();
        for (int i = 1; i < matches.size(); i++) {
            assertTrue(!matches.get(i).time().isBefore(matches.get(i - 1).time()),
                    "matches not ordered at index " + i);
        }
    }

    @Then("a NullPointerException is thrown")
    public void aNullPointerExceptionIsThrown() {
        assertTrue(thrown instanceof NullPointerException,
                "expected NullPointerException but was " + thrown);
    }

    @Then("both detection results are equal")
    public void bothDetectionResultsAreEqual() {
        assertTrue(result.equals(result2), "detection results are not equal");
    }

    @Then("all {int} detection results are equal")
    public void allDetectionResultsAreEqual(int count) {
        assertTrue(concurrentResults.size() == count,
                "result count: expected " + count + " but was " + concurrentResults.size());
        DetectionResult first = concurrentResults.get(0);
        for (DetectionResult other : concurrentResults) {
            assertTrue(other.equals(first), "concurrent detection results differ");
        }
    }

    @Then("every truncated match also appears in the full result")
    public void everyTruncatedMatchAppearsInFull() {
        for (PatternMatch match : truncatedMatches) {
            assertTrue(fullMatches.contains(match),
                    "truncated match absent from full result: " + match);
        }
    }

    @Then("every match has timeframe {string}")
    public void everyMatchHasTimeframe(String wire) {
        requireDetected();
        Optional<Timeframe> expected = Optional.of(Timeframe.fromWire(wire));
        assertTrue(!result.matches().isEmpty(), "no matches to check timeframe on");
        for (PatternMatch match : result.matches()) {
            assertTrue(match.timeframe().equals(expected),
                    "timeframe: expected " + expected + " but was " + match.timeframe());
        }
    }

    // --- helpers ---

    private void addRule(DetectionRule rule) {
        addedRules.add(rule);
        builder.addRule(rule);
    }

    private void setOhlcSeries(List<BigDecimal> closes) {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            BigDecimal c = closes.get(i);
            bars.add(new OHLCBar(timeOf(i), c, c, c, c, Optional.empty()));
        }
        ohlcBars = bars;
        builder.withSeries(new OHLCSeries(bars));
    }

    private static Instant timeOf(int barIndex) {
        return BASE.plusSeconds(barIndex * 86400L);
    }

    private long countOfType(String type) {
        return result.matches().stream()
                .filter(m -> m.getClass().getSimpleName().equals(type))
                .count();
    }

    private void requireDetected() {
        if (thrown != null) {
            throw new AssertionError("detection failed unexpectedly: " + thrown, thrown);
        }
        assertTrue(result != null, "no detection result available");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
