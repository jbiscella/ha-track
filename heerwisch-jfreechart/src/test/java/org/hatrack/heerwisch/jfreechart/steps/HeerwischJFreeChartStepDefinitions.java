package org.hatrack.heerwisch.jfreechart.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.error.DriverInternalException;
import org.hatrack.heerwisch.api.error.UnsupportedFeatureException;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
import org.hatrack.heerwisch.api.spec.FillColor;
import org.hatrack.heerwisch.api.spec.GlyphStyle;
import org.hatrack.heerwisch.api.spec.ImageFormat;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.LayoutSpec;
import org.hatrack.heerwisch.api.spec.MarkerDirection;
import org.hatrack.heerwisch.api.spec.Pane;
import org.hatrack.heerwisch.jfreechart.JFreeChartRenderer;
import org.hatrack.heerwisch.jfreechart.TestRenderers;
import org.hatrack.heerwisch.jfreechart.theme.ThemeConstants;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class HeerwischJFreeChartStepDefinitions {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final BigDecimal TWO = new BigDecimal("2");

    private ChartSpecBuilder builder;
    private ChartImage image;
    private ChartImage image2;
    private Exception thrown;

    // --- given ---

    @Given("a chart with an OHLC series of {int} bars")
    public void aChartWithOhlcSeries(int n) {
        builder = ChartSpec.builder();
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal base = new BigDecimal(100 + (i % 10));
            bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    base, base.add(TWO), base.subtract(TWO), base.add(BigDecimal.ONE),
                    Optional.of(new BigDecimal(1000 + i))));
        }
        builder.withSeries(new OHLCSeries(bars));
    }

    @Given("a {word} indicator placed at pane {word}")
    public void anIndicatorPlacedAtPane(String name, String pane) {
        builder.addIndicator(indicatorByName(name), Pane.valueOf(pane));
    }

    @Given("the layout is auto {int} by {int} with format {word}")
    public void theLayoutIsAuto(int width, int height, String format) {
        builder.withLayout(new LayoutSpec.AutoLayoutSpec(width, height, ImageFormat.valueOf(format)));
    }

    @Given("an EntryExitMarker at bar {int} with direction {word} and glyph {word}")
    public void anEntryExitMarker(int barIndex, String direction, String glyph) {
        builder.addAnnotation(new Annotation.EntryExitMarker(
                BASE.plusSeconds(barIndex * 86400L),
                new BigDecimal("100"),
                MarkerDirection.valueOf(direction),
                GlyphStyle.valueOf(glyph)));
    }

    @Given("a TimeRangeHighlight from bar {int} to bar {int} with fillColor {word} and opacity {bigdecimal}")
    public void aTimeRangeHighlight(int fromBar, int toBar, String fillColor, BigDecimal opacity) {
        builder.addAnnotation(new Annotation.TimeRangeHighlight(
                BASE.plusSeconds(fromBar * 86400L),
                BASE.plusSeconds(toBar * 86400L),
                FillColor.valueOf(fillColor),
                opacity));
    }

    @Given("java.awt.headless is set to true")
    public void javaAwtHeadlessIsSetToTrue() {
        System.setProperty("java.awt.headless", "true");
    }

    // --- when ---

    @When("I render the chart")
    public void iRenderTheChart() {
        thrown = null;
        try {
            image = new JFreeChartRenderer().render(builder.build());
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I render the chart twice with the same renderer")
    public void iRenderTheChartTwiceWithTheSameRenderer() throws Exception {
        ChartSpec spec = builder.build();
        JFreeChartRenderer renderer = new JFreeChartRenderer();
        image = renderer.render(spec);
        image2 = renderer.render(spec);
    }

    @When("I render the chart with two separate driver instances")
    public void iRenderTheChartWithTwoDriverInstances() throws Exception {
        ChartSpec spec = builder.build();
        image = new JFreeChartRenderer().render(spec);
        image2 = new JFreeChartRenderer().render(spec);
    }

    @When("I construct a renderer with a missing font resource")
    public void iConstructARendererWithAMissingFontResource() {
        thrown = null;
        try {
            TestRenderers.withFontResource("/heerwisch-fonts/does-not-exist.ttf");
        } catch (Exception e) {
            thrown = e;
        }
    }

    // --- then ---

    @Then("rendering succeeds")
    public void renderingSucceeds() {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(image != null, "no chart image produced");
    }

    @Then("no HeadlessException is thrown")
    public void noHeadlessExceptionIsThrown() {
        assertTrue(!(thrown instanceof java.awt.HeadlessException),
                "a HeadlessException was thrown");
    }

    @Then("the chart image contentType is {string}")
    public void theChartImageContentTypeIs(String contentType) {
        assertTrue(image.contentType().equals(contentType),
                "contentType: expected " + contentType + " but was " + image.contentType());
    }

    @Then("the chart image starts with the {word} magic bytes")
    public void theChartImageStartsWithMagicBytes(String format) {
        byte[] bytes = image.bytes();
        if (format.equals("PNG")) {
            assertTrue(bytes.length >= 4 && (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50
                            && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47,
                    "image does not start with PNG magic bytes");
        } else {
            assertTrue(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                            && (bytes[2] & 0xFF) == 0xFF,
                    "image does not start with JPEG magic bytes");
        }
    }

    @Then("the chart image is {int} by {int}")
    public void theChartImageIs(int width, int height) {
        assertTrue(thrown == null, "unexpected exception: " + thrown);
        assertTrue(image.widthPx() == width && image.heightPx() == height,
                "image size: expected " + width + "x" + height
                        + " but was " + image.widthPx() + "x" + image.heightPx());
    }

    @Then("an UnsupportedFeatureException is thrown")
    public void anUnsupportedFeatureExceptionIsThrown() {
        assertTrue(thrown instanceof UnsupportedFeatureException,
                "expected UnsupportedFeatureException but was " + thrown);
    }

    @Then("the exception featureName is {string}")
    public void theExceptionFeatureNameIs(String name) {
        String actual = ((UnsupportedFeatureException) thrown).featureName();
        assertTrue(name.equals(actual), "featureName: expected " + name + " but was " + actual);
    }

    @Then("the exception driverName is {string}")
    public void theExceptionDriverNameIs(String name) {
        String actual = ((UnsupportedFeatureException) thrown).driverName();
        assertTrue(name.equals(actual), "driverName: expected " + name + " but was " + actual);
    }

    @Then("both chart images are byte-identical")
    public void bothChartImagesAreByteIdentical() {
        assertTrue(Arrays.equals(image.bytes(), image2.bytes()),
                "chart images are not byte-identical");
    }

    @Then("a DriverInternalException is thrown")
    public void aDriverInternalExceptionIsThrown() {
        assertTrue(thrown instanceof DriverInternalException,
                "expected DriverInternalException but was " + thrown);
    }

    @Then("its cause is an IOException")
    public void itsCauseIsAnIoException() {
        assertTrue(thrown.getCause() instanceof IOException,
                "expected IOException cause but was " + thrown.getCause());
    }

    @Then("ThemeConstants color {word} equals hex {string}")
    public void themeConstantsColorEqualsHex(String name, String hex) throws Exception {
        Color color = (Color) ThemeConstants.class.getField(name).get(null);
        int expected = Integer.parseInt(hex, 16);
        assertTrue((color.getRGB() & 0xFFFFFF) == expected,
                name + ": expected " + hex + " but was "
                        + Integer.toHexString(color.getRGB() & 0xFFFFFF));
    }

    @Then("every ThemeConstants color field is public static and final")
    public void everyThemeConstantsColorFieldIsPublicStaticFinal() {
        boolean found = false;
        for (Field field : ThemeConstants.class.getDeclaredFields()) {
            if (field.getType() == Color.class) {
                found = true;
                int modifiers = field.getModifiers();
                assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                                && Modifier.isFinal(modifiers),
                        field.getName() + " is not public static final");
            }
        }
        assertTrue(found, "no Color fields found on ThemeConstants");
    }

    // --- helpers ---

    private static Indicator indicatorByName(String name) {
        return switch (name) {
            case "SMA" -> new Indicator.SMA(20, PriceSource.CLOSE);
            case "EMA" -> new Indicator.EMA(50, PriceSource.CLOSE);
            case "BollingerBands" -> new Indicator.BollingerBands(20, TWO, PriceSource.CLOSE);
            case "RSI" -> new Indicator.RSI(14, new BigDecimal("70"), new BigDecimal("30"),
                    PriceSource.CLOSE);
            case "MACD" -> new Indicator.MACD(12, 26, 9, PriceSource.CLOSE);
            case "ADX" -> new Indicator.ADX(14);
            case "Stochastic" -> new Indicator.Stochastic(14, 3, 3);
            case "ATR" -> new Indicator.ATR(14);
            case "VolumePane" -> new Indicator.VolumePane();
            default -> throw new IllegalArgumentException("unknown indicator: " + name);
        };
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
