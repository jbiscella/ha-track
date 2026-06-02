package org.hatrack.dsl;

import org.hatrack.dsl.error.DslEvaluationException;
import org.hatrack.dsl.error.IndicatorWarmupException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeamTypesTest {

    @Test
    void expressionRefRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new ExpressionRef(null, "1h"));
        assertThrows(NullPointerException.class, () -> new ExpressionRef("x", null));
        ExpressionRef ref = new ExpressionRef("close is above sma(20)", "1h");
        assertEquals("close is above sma(20)", ref.text());
        assertEquals("1h", ref.timeframeWire());
    }

    @Test
    void dslExceptionCarriesContext() {
        Instant t = Instant.parse("2024-06-01T00:00:00Z");
        DslEvaluationException e = new DslEvaluationException(
                "myStrategy", 3, t, 42, "1 / 0", "division by zero");
        assertEquals("myStrategy", e.source());
        assertEquals(3, e.lineNumber());
        assertEquals(t, e.barTime());
        assertEquals(42, e.barIndex());
        assertEquals("1 / 0", e.expressionText());
        assertEquals("division by zero", e.getMessage());
        assertNull(e.getCause());

        Throwable cause = new RuntimeException("boom");
        DslEvaluationException wrapped = new DslEvaluationException(
                "s", 0, t, 0, "expr", "msg", cause);
        assertSameCause(cause, wrapped);
    }

    private static void assertSameCause(Throwable cause, DslEvaluationException e) {
        assertNotNull(e.getCause());
        assertEquals(cause, e.getCause());
    }

    @Test
    void warmupExceptionMessage() {
        IndicatorWarmupException e = new IndicatorWarmupException("not enough bars");
        assertEquals("not enough bars", e.getMessage());
    }
}
