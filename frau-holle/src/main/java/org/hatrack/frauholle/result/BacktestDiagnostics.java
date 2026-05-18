package org.hatrack.frauholle.result;

/**
 * Informational counters about signals that were ignored, no-op or unfilled.
 *
 * <p>{@code forcedClosesAtExplicitPrice} is a v1.1 additive counter; a backtest
 * using only v1 signal variants leaves it at 0.
 */
public record BacktestDiagnostics(int ignoredBuySignals, int ignoredSellSignals,
                                  int noOpClosePositionSignals,
                                  int unfilledSignalsAtEndOfSeries,
                                  int forcedClosesAtExplicitPrice) {
}
