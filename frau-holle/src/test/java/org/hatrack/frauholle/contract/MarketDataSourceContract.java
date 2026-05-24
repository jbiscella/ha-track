package org.hatrack.frauholle.contract;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.error.MarketDataException;
import org.hatrack.frauholle.port.MarketDataSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared conformance suite for every {@link MarketDataSource} implementation.
 *
 * <p>It asserts the port output invariants from {@code frau-holle/CLAUDE.md}
 * §2.1 on well-formed input: the result is a non-null list of non-null bars,
 * ordered by strictly-ascending (hence unique) time, every bar inside the
 * requested {@code [since, until]}, and an empty range yields an empty list
 * rather than an error.
 *
 * <p>Driver-specific <em>normalization</em> policy — how a driver reaches those
 * invariants from a messy feed (sorting, de-duplication, skipping malformed
 * rows, schema rejection) — is explicitly NOT covered here; that varies per
 * driver and is exercised by each driver's own tests.
 *
 * <p>Each driver module contributes a concrete subclass that wires a source to
 * well-formed data and supplies one {@link #populatedQuery() populated} query
 * plus one guaranteed-empty {@link #emptyQuery() empty} query.
 */
public abstract class MarketDataSourceContract {

    /** A single {@code fetchHistory} request. */
    public record Query(String symbol, Timeframe timeframe, Instant since, Instant until) {}

    /** The source under test, wired to well-formed data covering {@link #populatedQuery()}. */
    protected abstract MarketDataSource source();

    /** A query the source answers with at least one bar. */
    protected abstract Query populatedQuery();

    /** A query over a range the source has no data for; must yield an empty list. */
    protected abstract Query emptyQuery();

    private List<OHLCBar> fetch(Query q) throws MarketDataException {
        return source().fetchHistory(q.symbol(), q.timeframe(), q.since(), q.until());
    }

    @Test
    void populated_query_returns_a_non_null_list() throws Exception {
        assertNotNull(fetch(populatedQuery()), "fetchHistory must never return null");
    }

    @Test
    void populated_query_is_not_vacuously_empty() throws Exception {
        // Guards the remaining populated-query assertions from passing trivially
        // on an empty list — the contract is only meaningful with real bars.
        assertFalse(fetch(populatedQuery()).isEmpty(),
                "the populated query is expected to return at least one bar");
    }

    @Test
    void result_contains_no_null_bars() throws Exception {
        for (OHLCBar bar : fetch(populatedQuery())) {
            assertNotNull(bar, "the result must not contain null bars");
        }
    }

    @Test
    void times_are_strictly_ascending_and_unique() throws Exception {
        List<OHLCBar> bars = fetch(populatedQuery());
        for (int i = 1; i < bars.size(); i++) {
            Instant prev = bars.get(i - 1).time();
            Instant curr = bars.get(i).time();
            assertTrue(curr.isAfter(prev),
                    "bar times must be strictly ascending and unique; index " + i + " (" + curr
                            + ") is not strictly after index " + (i - 1) + " (" + prev + ")");
        }
    }

    @Test
    void every_bar_is_within_the_requested_range() throws Exception {
        Query q = populatedQuery();
        for (OHLCBar bar : fetch(q)) {
            Instant t = bar.time();
            assertFalse(t.isBefore(q.since()), "bar time " + t + " is before since " + q.since());
            assertFalse(t.isAfter(q.until()), "bar time " + t + " is after until " + q.until());
        }
    }

    @Test
    void empty_range_returns_an_empty_list_not_an_error() throws Exception {
        List<OHLCBar> bars = fetch(emptyQuery());
        assertNotNull(bars, "an empty range must return an empty list, not null");
        assertTrue(bars.isEmpty(), "an empty range must return an empty list");
    }
}
