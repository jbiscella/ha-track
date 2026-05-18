package org.hatrack.frauholle.result;

/** Informational counters about signals that were ignored, no-op or unfilled. */
public record BacktestDiagnostics(int ignoredBuySignals, int ignoredSellSignals,
                                  int noOpClosePositionSignals,
                                  int unfilledSignalsAtEndOfSeries) {
}
