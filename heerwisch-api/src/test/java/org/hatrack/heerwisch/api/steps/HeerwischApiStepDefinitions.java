package org.hatrack.heerwisch.api.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.error.DriverInternalException;
import org.hatrack.heerwisch.api.error.InvalidChartSpecException;
import org.hatrack.heerwisch.api.error.UnsupportedFeatureException;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.LayoutSpec;
import org.hatrack.heerwisch.api.spec.LayoutSpecBuilder;
import org.hatrack.heerwisch.api.spec.LevelStyle;
import org.hatrack.heerwisch.api.spec.Pane;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HeerwischApiStepDefinitions {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    private ChartSpecBuilder builder;
    private LayoutSpecBuilder layoutBuilder;
    private ChartSpec spec;
    private LayoutSpec defaultLayout;
    private ChartImage image;
    private ChartImage image2;
    private UnsupportedFeatureException unsupportedFeature;
    private DriverInternalException driverInternal;
    private RuntimeException internalCause;
    private Exception thrown;

    // --- builder / series ---

    @Given("a chart spec builder")
    public void aChartSpecBuilder() {
        builder = ChartSpec.builder();
        spec = null;
        thrown = null;
    }

    @Given("an empty OHLC series")
    public void anEmptyOhlcSeries() {
        builder.withSeries(new OHLCSeries(List.of()));
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
        builder.withSeries(new OHLCSeries(bars));
    }

    @Given("an OHLC series of {int} bars")
    public void anOhlcSeries(int n) {
        builder.withSeries(new OHLCSeries(ohlcBars(n, true)));
    }

    @Given("an OHLC series of {int} bars without volume")
    public void anOhlcSeriesWithoutVolume(int n) {
        builder.withSeries(new OHLCSeries(ohlcBars(n, false)));
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
        builder.withSeries(new OHLCSeries(bars));
    }

    @Given("an HA series of {int} bars")
    public void anHaSeries(int n) {
        List<HABar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal v = new BigDecimal(100 + i);
            bars.add(new HABar(timeOf(i), v, v, v, v));
        }
        builder.withSeries(new HASeries(bars));
    }

    // --- indicators ---

    @Given("an SMA indicator with period {int} and source {word}")
    public void smaIndicator(int period, String source) {
        builder.addIndicator(new Indicator.SMA(period, PriceSource.valueOf(source)));
    }

    @Given("an EMA indicator with period {int} and source {word}")
    public void emaIndicator(int period, String source) {
        builder.addIndicator(new Indicator.EMA(period, PriceSource.valueOf(source)));
    }

    @Given("a BollingerBands indicator with period {int} stdDev {bigdecimal} and source {word}")
    public void bollingerBandsIndicator(int period, BigDecimal stdDev, String source) {
        builder.addIndicator(new Indicator.BollingerBands(period, stdDev, PriceSource.valueOf(source)));
    }

    @Given("an RSI indicator with period {int} overbought {bigdecimal} oversold {bigdecimal} and source {word}")
    public void rsiIndicator(int period, BigDecimal overbought, BigDecimal oversold, String source) {
        builder.addIndicator(new Indicator.RSI(period, overbought, oversold, PriceSource.valueOf(source)));
    }

    @Given("an RSI indicator with period {int} overbought {bigdecimal} oversold {bigdecimal} "
            + "and source {word} placed at pane {word}")
    public void rsiIndicatorAtPane(int period, BigDecimal overbought, BigDecimal oversold,
                                   String source, String pane) {
        builder.addIndicator(new Indicator.RSI(period, overbought, oversold, PriceSource.valueOf(source)),
                Pane.valueOf(pane));
    }

    @Given("a MACD indicator with fast {int} slow {int} signal {int} and source {word}")
    public void macdIndicator(int fast, int slow, int signal, String source) {
        builder.addIndicator(new Indicator.MACD(fast, slow, signal, PriceSource.valueOf(source)));
    }

    @Given("an ADX indicator with period {int}")
    public void adxIndicator(int period) {
        builder.addIndicator(new Indicator.ADX(period));
    }

    @Given("a Stochastic indicator with k {int} d {int} and smoothing {int}")
    public void stochasticIndicator(int k, int d, int smoothing) {
        builder.addIndicator(new Indicator.Stochastic(k, d, smoothing));
    }

    @Given("an ATR indicator with period {int}")
    public void atrIndicator(int period) {
        builder.addIndicator(new Indicator.ATR(period));
    }

    @Given("a VolumePane indicator")
    public void volumePaneIndicator() {
        builder.addIndicator(new Indicator.VolumePane());
    }

    // --- annotations ---

    @Given("a BarHighlight annotation at time {string}")
    public void barHighlightAnnotation(String time) {
        builder.addAnnotation(new Annotation.BarHighlight(Instant.parse(time), BigDecimal.TEN, ""));
    }

    @Given("a HorizontalLevel annotation at price {bigdecimal}")
    public void horizontalLevelAnnotation(BigDecimal price) {
        builder.addAnnotation(new Annotation.HorizontalLevel(price, "", LevelStyle.SOLID));
    }

    // --- layout ---

    @Given("an explicit layout with mainPaneHeight {bigdecimal} and subplot {word} height {bigdecimal}")
    public void anExplicitLayout(BigDecimal mainHeight, String pane, BigDecimal subplotHeight) {
        builder.withLayout(new LayoutSpec.ExplicitLayoutSpec(900, 500, mainHeight,
                Map.of(Pane.valueOf(pane), subplotHeight), org.hatrack.heerwisch.api.spec.ImageFormat.JPEG));
    }

    // --- actions ---

    @When("I build the chart spec")
    public void iBuildTheChartSpec() {
        thrown = null;
        try {
            spec = builder.build();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Given("a layout builder with a subplot height but no main pane height")
    public void aLayoutBuilderWithASubplotHeightButNoMainPaneHeight() {
        layoutBuilder = LayoutSpec.builder()
                .addSubplotHeight(Pane.SUBPLOT_1, new BigDecimal("0.4"));
    }

    @When("I build the layout")
    public void iBuildTheLayout() {
        thrown = null;
        try {
            layoutBuilder.build();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("no NullPointerException is thrown")
    public void noNullPointerExceptionIsThrown() {
        assertTrue(!(thrown instanceof NullPointerException),
                "a NullPointerException was thrown: " + thrown);
    }

    @When("I get the default layout")
    public void iGetTheDefaultLayout() {
        defaultLayout = LayoutSpec.defaults();
    }

    @When("I render the spec with the reference renderer")
    public void iRenderWithReferenceRenderer() {
        thrown = null;
        try {
            image = new FakeChartRenderer().render(spec);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I render a null spec with the reference renderer")
    public void iRenderNullSpec() {
        thrown = null;
        try {
            new FakeChartRenderer().render(null);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I render the spec with the reference renderer twice")
    public void iRenderTwice() throws Exception {
        FakeChartRenderer renderer = new FakeChartRenderer();
        image = renderer.render(spec);
        image2 = renderer.render(spec);
    }

    @When("an UnsupportedFeatureException is created for feature {string} and driver {string}")
    public void anUnsupportedFeatureExceptionIsCreated(String feature, String driver) {
        unsupportedFeature = new UnsupportedFeatureException(feature, driver);
    }

    @When("a DriverInternalException is created wrapping an internal error")
    public void aDriverInternalExceptionIsCreated() {
        internalCause = new IllegalStateException("internal failure");
        driverInternal = new DriverInternalException(internalCause);
    }

    // --- assertions ---

    @Then("an InvalidChartSpecException is thrown with violatedRule {string}")
    public void invalidChartSpecWithViolatedRule(String rule) {
        assertTrue(thrown instanceof InvalidChartSpecException,
                "expected InvalidChartSpecException but was " + thrown);
        String actual = ((InvalidChartSpecException) thrown).violatedRule();
        assertTrue(rule.equals(actual), "violatedRule: expected " + rule + " but was " + actual);
    }

    @Then("the chart spec builds successfully")
    public void theChartSpecBuildsSuccessfully() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(spec != null, "chart spec was not built");
    }

    @Then("the chart spec has {int} indicators")
    public void theChartSpecHasIndicators(int n) {
        assertTrue(spec.indicators().size() == n,
                "indicator count: expected " + n + " but was " + spec.indicators().size());
    }

    @Then("indicator {int} is placed at pane {word}")
    public void indicatorIsPlacedAtPane(int index, String pane) {
        Pane actual = spec.indicators().get(index).pane();
        assertTrue(actual.name().equals(pane),
                "indicator[" + index + "] pane: expected " + pane + " but was " + actual);
    }

    @Then("the layout is an AutoLayoutSpec with width {int} and height {int}")
    public void theLayoutIsAutoLayoutSpec(int width, int height) {
        assertTrue(spec.layout() instanceof LayoutSpec.AutoLayoutSpec,
                "expected AutoLayoutSpec but was " + spec.layout());
        assertTrue(spec.layout().widthPx() == width && spec.layout().heightPx() == height,
                "layout size: expected " + width + "x" + height
                        + " but was " + spec.layout().widthPx() + "x" + spec.layout().heightPx());
    }

    @Then("the layout format is {word}")
    public void theLayoutFormatIs(String format) {
        assertTrue(spec.layout().format().name().equals(format),
                "layout format: expected " + format + " but was " + spec.layout().format());
    }

    @Then("the default layout is an AutoLayoutSpec with width {int} and height {int}")
    public void theDefaultLayoutIsAutoLayoutSpec(int width, int height) {
        assertTrue(defaultLayout instanceof LayoutSpec.AutoLayoutSpec,
                "expected AutoLayoutSpec but was " + defaultLayout);
        assertTrue(defaultLayout.widthPx() == width && defaultLayout.heightPx() == height,
                "default layout size mismatch");
    }

    @Then("the default layout format is {word}")
    public void theDefaultLayoutFormatIs(String format) {
        assertTrue(defaultLayout.format().name().equals(format),
                "default layout format: expected " + format + " but was " + defaultLayout.format());
    }

    @Then("the chart image is not null")
    public void theChartImageIsNotNull() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(image != null, "chart image is null");
    }

    @Then("the chart image bytes are not empty")
    public void theChartImageBytesAreNotEmpty() {
        assertTrue(image.bytes().length > 0, "chart image bytes are empty");
    }

    @Then("the chart image contentType is {string}")
    public void theChartImageContentTypeIs(String contentType) {
        assertTrue(image.contentType().equals(contentType),
                "contentType: expected " + contentType + " but was " + image.contentType());
    }

    @Then("the chart image dimensions are positive")
    public void theChartImageDimensionsArePositive() {
        assertTrue(image.widthPx() > 0 && image.heightPx() > 0,
                "chart image dimensions are not positive");
    }

    @Then("a NullPointerException is thrown")
    public void aNullPointerExceptionIsThrown() {
        assertTrue(thrown instanceof NullPointerException,
                "expected NullPointerException but was " + thrown);
    }

    @Then("both chart images are byte-identical")
    public void bothChartImagesAreByteIdentical() {
        assertTrue(Arrays.equals(image.bytes(), image2.bytes()),
                "chart image bytes are not identical");
    }

    @Then("its featureName is {string}")
    public void itsFeatureNameIs(String name) {
        assertTrue(unsupportedFeature.featureName().equals(name),
                "featureName: expected " + name + " but was " + unsupportedFeature.featureName());
    }

    @Then("its driverName is {string}")
    public void itsDriverNameIs(String name) {
        assertTrue(unsupportedFeature.driverName().equals(name),
                "driverName: expected " + name + " but was " + unsupportedFeature.driverName());
    }

    @Then("its cause is that internal error")
    public void itsCauseIsThatInternalError() {
        assertTrue(driverInternal.getCause() == internalCause,
                "DriverInternalException cause mismatch");
    }

    // --- helpers ---

    private static List<OHLCBar> ohlcBars(int n, boolean withVolume) {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal v = new BigDecimal(100 + i);
            Optional<BigDecimal> volume = withVolume ? Optional.of(new BigDecimal("1000")) : Optional.empty();
            bars.add(new OHLCBar(timeOf(i), v, v, v, v, volume));
        }
        return bars;
    }

    private static Instant timeOf(int barIndex) {
        return BASE.plusSeconds(barIndex * 86400L);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
