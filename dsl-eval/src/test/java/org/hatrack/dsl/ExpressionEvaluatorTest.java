package org.hatrack.dsl;

import org.hatrack.dsl.error.DslEvaluationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {

    private final ExpressionEvaluator eval =
            new ExpressionEvaluator("strat", Instant.EPOCH, 7);

    /** A Scope backed by a simple identifier map and a function map. */
    private static ExpressionEvaluator.Scope scope(Map<String, BigDecimal> values,
                                                   Map<String, BigDecimal> functions) {
        ExpressionEvaluator.Values v = id -> {
            BigDecimal x = values.get(id);
            if (x == null) {
                throw new IllegalArgumentException("unknown identifier: " + id);
            }
            return x;
        };
        ExpressionEvaluator.IndicatorSource ind = (name, args) -> {
            BigDecimal x = functions.get(name);
            if (x == null) {
                throw new IllegalStateException("unknown function: " + name);
            }
            return x;
        };
        return new ExpressionEvaluator.Scope(v, ind);
    }

    private ExpressionEvaluator.Scope numbers() {
        return scope(Map.of("close", new BigDecimal("10"), "open", new BigDecimal("4"),
                "x", new BigDecimal("3")), Map.of("sma", new BigDecimal("8")));
    }

    @Test
    void arithmeticPrecedenceAndParens() {
        ExpressionEvaluator.Scope s = numbers();
        assertEquals(0, eval.arithmetic("2 + 3 * 4", s).compareTo(new BigDecimal("14")));
        assertEquals(0, eval.arithmetic("(2 + 3) * 4", s).compareTo(new BigDecimal("20")));
        assertEquals(0, eval.arithmetic("10 / 4", s).compareTo(new BigDecimal("2.5")));
        assertEquals(0, eval.arithmetic("-x + 5", s).compareTo(new BigDecimal("2")));
        assertEquals(0, eval.arithmetic("close - open", s).compareTo(new BigDecimal("6")));
    }

    @Test
    void functionCallWithAndWithoutArgs() {
        ExpressionEvaluator.Scope s = numbers();
        assertEquals(0, eval.arithmetic("sma(14)", s).compareTo(new BigDecimal("8")));
        assertEquals(0, eval.arithmetic("sma(14, 2) + close", s).compareTo(new BigDecimal("18")));
    }

    @Test
    void comparisonOperators() {
        ExpressionEvaluator.Scope s = numbers();
        assertTrue(eval.condition("close is above open", s, null));
        assertTrue(eval.condition("close exceeds open", s, null));
        assertFalse(eval.condition("close is below open", s, null));
        assertTrue(eval.condition("open is below close", s, null));
    }

    @Test
    void crossingNeedsPreviousBar() {
        ExpressionEvaluator.Scope prev = scope(Map.of("a", new BigDecimal("1"),
                "b", new BigDecimal("2")), Map.of());
        ExpressionEvaluator.Scope cur = scope(Map.of("a", new BigDecimal("3"),
                "b", new BigDecimal("2")), Map.of());
        assertTrue(eval.condition("a crosses above b", cur, prev));
        assertTrue(eval.condition("a rises above b", cur, prev));
        // No previous → crossing is false, never throws.
        assertFalse(eval.condition("a crosses above b", cur, null));
        // Reverse direction.
        assertTrue(eval.condition("b crosses below a", cur, prev));
        assertFalse(eval.condition("b drops below a", cur, null));
    }

    @Test
    void bareNonZeroIsTruthy() {
        ExpressionEvaluator.Scope truthy = scope(Map.of(), Map.of("ha_doji", BigDecimal.ONE));
        ExpressionEvaluator.Scope falsy = scope(Map.of(), Map.of("ha_doji", BigDecimal.ZERO));
        assertTrue(eval.condition("ha_doji()", truthy, null));
        assertFalse(eval.condition("ha_doji()", falsy, null));
    }

    @Test
    void divisionByZeroThrows() {
        ExpressionEvaluator.Scope s = numbers();
        DslEvaluationException e = assertThrows(DslEvaluationException.class,
                () -> eval.arithmetic("1 / 0", s));
        assertTrue(e.getMessage().contains("division by zero"));
        assertEquals("strat", e.source());
        assertEquals(7, e.barIndex());
    }

    @Test
    void malformedExpressionsThrow() {
        ExpressionEvaluator.Scope s = numbers();
        assertThrows(DslEvaluationException.class, () -> eval.arithmetic("(1 + 2", s));
        assertThrows(DslEvaluationException.class, () -> eval.arithmetic("1 +", s));
        assertThrows(DslEvaluationException.class, () -> eval.arithmetic("1 2", s));
        assertThrows(DslEvaluationException.class, () -> eval.arithmetic("@", s));
        assertThrows(DslEvaluationException.class, () -> eval.arithmetic("sma(1", s));
    }

    @Test
    void malformedNumericLiteralWrappedInDslError() {
        ExpressionEvaluator.Scope s = numbers();
        DslEvaluationException e = assertThrows(DslEvaluationException.class,
                () -> eval.arithmetic("1..2", s));
        assertTrue(e.getMessage().contains("malformed numeric literal"));
        assertEquals("strat", e.source());
    }

    @Test
    void unaryNegationStaysDecimal64() {
        // A >16-significant-digit identifier negated then compared: negation must
        // apply DECIMAL64 like the surrounding operators (parity discipline).
        BigDecimal hi = new BigDecimal("1.234567890123456789");
        ExpressionEvaluator.Scope s = scope(Map.of("x", hi), Map.of());
        BigDecimal negated = eval.arithmetic("-x", s);
        assertEquals(hi.negate(java.math.MathContext.DECIMAL64), negated);
    }

    @Test
    void pivotStepRoutesToPivotPrimitive() {
        ExpressionEvaluator.IndicatorSource ind = new ExpressionEvaluator.IndicatorSource() {
            @Override
            public BigDecimal evaluate(String functionName, List<BigDecimal> arguments) {
                throw new AssertionError("should not be called for a pivot step");
            }

            @Override
            public boolean pivotPrimitive(String name, String level) {
                return name.equals("price_above_pivot") && level.equals("R1");
            }
        };
        ExpressionEvaluator.Scope s =
                new ExpressionEvaluator.Scope(id -> BigDecimal.ZERO, ind);
        assertTrue(eval.condition("price_above_pivot(R1)", s, null));
    }

    @Test
    void defaultPivotPrimitiveThrows() {
        ExpressionEvaluator.IndicatorSource ind = (name, args) -> BigDecimal.ZERO;
        assertThrows(UnsupportedOperationException.class,
                () -> ind.pivotPrimitive("price_above_pivot", "R1"));
    }
}
