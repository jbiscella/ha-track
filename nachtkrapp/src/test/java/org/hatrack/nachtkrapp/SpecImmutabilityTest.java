package org.hatrack.nachtkrapp;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.nachtkrapp.detector.DetectionResult;
import org.hatrack.nachtkrapp.detector.RuleBasedPatternDetector;
import org.hatrack.nachtkrapp.match.PatternMatch;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.rule.MAType;
import org.hatrack.nachtkrapp.spec.DetectionSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Immutability contracts for the records carrying collections: {@code
 * DetectionSpec.rules()} and {@code DetectionResult.matches()} must be
 * defensively copied and unmodifiable, so a caller cannot mutate a built spec
 * (or a returned result) by holding the source list or the accessor's return.
 * Pure JUnit — object-contract assertions.
 */
class SpecImmutabilityTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    private static OHLCSeries series(int n) {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal c = new BigDecimal(100 + i);
            bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L), c, c, c, c, Optional.empty()));
        }
        return new OHLCSeries(bars);
    }

    @Test
    void detectionSpecCopiesTheRuleListAndExposesItUnmodifiable() throws Exception {
        List<DetectionRule> source = new ArrayList<>();
        source.add(new DetectionRule.PriceVsMARule(MAType.SMA, 5, PriceSource.CLOSE));
        source.add(new DetectionRule.PriceVsMARule(MAType.EMA, 5, PriceSource.CLOSE));

        DetectionSpec spec = DetectionSpec.builder()
                .withSeries(series(20))
                .addAllRules(source)
                .build();

        // mutating the source after build must not affect the spec
        int built = spec.rules().size();
        source.clear();
        assertEquals(built, spec.rules().size(), "spec must not share the caller's list");

        // the accessor's list must be unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> spec.rules().add(new DetectionRule.PriceVsMARule(MAType.SMA, 9, PriceSource.CLOSE)));
    }

    @Test
    void detectionResultMatchesAreUnmodifiable() throws Exception {
        DetectionSpec spec = DetectionSpec.builder()
                .withSeries(series(20))
                .addRule(new DetectionRule.PriceVsMARule(MAType.SMA, 5, PriceSource.CLOSE))
                .build();
        DetectionResult result = new RuleBasedPatternDetector().detect(spec);

        List<PatternMatch> matches = result.matches();
        assertThrows(UnsupportedOperationException.class, () -> matches.add(null));
    }
}
