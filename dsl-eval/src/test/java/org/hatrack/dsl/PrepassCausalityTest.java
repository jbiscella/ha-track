package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity port of wichtelm-app's {@code NachtkrappPrepassCausalityTest} — the
 * load-bearing lookahead-safety guard for the promoted prepass.
 *
 * <p>{@link NachtkrappMatchIndex#buildFor} builds the match index ONCE against
 * the full primary series. That is only equivalent to per-bar evaluation if
 * every nachtkrapp rule it uses is CAUSAL — each match's instant depends only on
 * bars at or before that instant. This builds the index twice (full series vs.
 * the first {@code k} bars) and asserts that for every instant up to the
 * prefix's end, both passes agree. A failure means a rule looked forward and the
 * repo-wide lookahead-safety invariant is broken.
 */
class PrepassCausalityTest {

    private static final String TF = "1h";

    /** Both detection branches: an HA rule (ha_doji) and a non-HA rule (rsi_oversold). */
    private static final List<ExpressionRef> EXPRESSIONS =
            List.of(new ExpressionRef("ha_doji() and rsi_oversold(30)", TF));

    private static final BigDecimal POINT_FIVE = new BigDecimal("0.5");
    private static final BigDecimal POINT_THREE = new BigDecimal("0.3");
    private static final BigDecimal POINT_ONE = new BigDecimal("0.1");

    /** 200 meandering bars with a sustained mid-series dip (drives RSI oversold). */
    private static final List<OHLCBar> SERIES = buildSeries(200);

    private static List<OHLCBar> buildSeries(int barCount) {
        List<OHLCBar> bars = new ArrayList<>(barCount);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        BigDecimal price = new BigDecimal("100");
        for (int i = 0; i < barCount; i++) {
            BigDecimal drift;
            if (i >= 60 && i < 120) {
                drift = POINT_THREE.negate();
            } else if (i % 5 == 0) {
                drift = POINT_FIVE;
            } else {
                drift = i % 2 == 0 ? POINT_ONE.negate() : POINT_ONE;
            }
            BigDecimal open = price;
            BigDecimal close = price.add(drift);
            BigDecimal high = open.max(close).add(POINT_THREE);
            BigDecimal low = open.min(close).subtract(POINT_THREE);
            bars.add(new OHLCBar(start.plus(Duration.ofHours(i)), open, high, low, close,
                    Optional.empty()));
            price = close;
        }
        return List.copyOf(bars);
    }

    private static NachtkrappMatchIndex indexOver(List<OHLCBar> bars) {
        return NachtkrappMatchIndex.buildFor(EXPRESSIONS, Map.of(), bars, TF, Map.of());
    }

    private static final NachtkrappMatchIndex.Key DOJI =
            new NachtkrappMatchIndex.Key("ha_doji", List.of(), TF);
    private static final NachtkrappMatchIndex.Key OVERSOLD =
            new NachtkrappMatchIndex.Key("rsi_oversold", List.of(new BigDecimal("30")), TF);

    @ParameterizedTest(name = "prepass(prefix k={0}) ≡ prepass(full)[t ≤ k-1]")
    @ValueSource(ints = { 30, 100, 150, 199 })
    void prepassOnPrefixMatchesFullSeriesUpToPrefixEnd(int k) {
        List<OHLCBar> prefix = SERIES.subList(0, k);
        NachtkrappMatchIndex prefixIndex = indexOver(prefix);
        NachtkrappMatchIndex fullIndex = indexOver(SERIES);

        assertCausalForKey(prefixIndex, fullIndex, DOJI, prefix, k);
        assertCausalForKey(prefixIndex, fullIndex, OVERSOLD, prefix, k);
    }

    private static void assertCausalForKey(NachtkrappMatchIndex prefixIndex,
                                           NachtkrappMatchIndex fullIndex,
                                           NachtkrappMatchIndex.Key key,
                                           List<OHLCBar> prefix,
                                           int k) {
        assertNotNull(prefixIndex);
        assertNotNull(fullIndex);
        for (OHLCBar bar : prefix) {
            Instant t = bar.time();
            boolean prefixHit = prefixIndex.matches(key, t);
            boolean fullHit = fullIndex.matches(key, t);
            assertEquals(fullHit, prefixHit,
                    () -> "non-causal rule for " + key.name() + " at " + t + " (prefix k=" + k + ")");
        }
    }

    @Test
    void prefixAndFullProduceIdenticalKeySetsForReferencedCalls() {
        List<OHLCBar> prefix = SERIES.subList(0, 80);
        NachtkrappMatchIndex prefixIndex = indexOver(prefix);
        NachtkrappMatchIndex fullIndex = indexOver(SERIES);

        // Both indices must KNOW the call (the scan registered it), whether or
        // not the rule actually fired during the prefix.
        assertTrue(prefixIndex.hasKey(DOJI),
                "ha_doji() must be indexed even when it never fires on the prefix");
        assertTrue(fullIndex.hasKey(DOJI));
    }
}
