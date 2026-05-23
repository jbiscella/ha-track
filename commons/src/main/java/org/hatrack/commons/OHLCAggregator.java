package org.hatrack.commons;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resamples an intraday {@link OHLCSeries} to a coarser period. UTC-only (v1):
 * {@code DAY} buckets by the UTC calendar day boundary (00:00:00Z); {@code WEEK}
 * buckets by the ISO week starting Monday 00:00:00Z. Other units, and any
 * {@code amount != 1}, are rejected.
 *
 * <p>One output bar is produced per period that contains at least one input
 * bar (empty periods between gaps are skipped); its {@code time} is the period
 * start. Within a period: {@code open} = first bar's open, {@code high} = max
 * high, {@code low} = min low, {@code close} = last bar's close, {@code volume}
 * = sum when every bar in the period carries volume, else empty. Output bars are
 * ordered ascending by time. Lookahead-safe: each output bar is a pure function
 * of the input bars that fall inside its period. RTH/session filtering is out of
 * scope; the caller filters upstream if needed.
 */
public final class OHLCAggregator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private OHLCAggregator() {
    }

    public static OHLCSeries toPeriod(OHLCSeries intraday, Timeframe period) {
        Objects.requireNonNull(intraday, "intraday");
        Objects.requireNonNull(period, "period");
        if (period.amount() != 1
                || (period.unit() != Timeframe.Unit.DAY && period.unit() != Timeframe.Unit.WEEK)) {
            throw new IllegalArgumentException(
                    "unsupported aggregation period (v1 supports 1d and 1w): " + period.wire());
        }
        List<OHLCBar> out = new ArrayList<>();
        List<OHLCBar> bucket = new ArrayList<>();
        Instant currentStart = null;
        for (OHLCBar bar : intraday.bars()) {
            Instant start = periodStart(bar.time(), period.unit());
            if (currentStart == null) {
                currentStart = start;
            } else if (!start.equals(currentStart)) {
                out.add(aggregate(currentStart, bucket));
                bucket.clear();
                currentStart = start;
            }
            bucket.add(bar);
        }
        if (!bucket.isEmpty()) {
            out.add(aggregate(currentStart, bucket));
        }
        return new OHLCSeries(out);
    }

    private static Instant periodStart(Instant time, Timeframe.Unit unit) {
        return switch (unit) {
            case DAY -> time.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> {
                LocalDate day = time.atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield monday.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            default -> throw new IllegalArgumentException("unsupported aggregation unit: " + unit);
        };
    }

    private static OHLCBar aggregate(Instant start, List<OHLCBar> bars) {
        BigDecimal open = bars.get(0).open();
        BigDecimal close = bars.get(bars.size() - 1).close();
        BigDecimal high = bars.get(0).high();
        BigDecimal low = bars.get(0).low();
        boolean allVolume = true;
        BigDecimal volumeSum = BigDecimal.ZERO;
        for (OHLCBar bar : bars) {
            if (bar.high().compareTo(high) > 0) {
                high = bar.high();
            }
            if (bar.low().compareTo(low) < 0) {
                low = bar.low();
            }
            if (bar.volume().isPresent()) {
                volumeSum = volumeSum.add(bar.volume().get(), MC);
            } else {
                allVolume = false;
            }
        }
        Optional<BigDecimal> volume = allVolume ? Optional.of(volumeSum) : Optional.empty();
        return new OHLCBar(start, open, high, low, close, volume);
    }
}
