package org.folio.circulation.resources.renewal;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public final class BulkRenewalPerformanceLogger {
  public static final Marker BULK_RENEWAL_PERF_ANALYSIS = MarkerManager.getMarker(
    "BULK_RENEWAL_PERF_ANALYSIS");

  private BulkRenewalPerformanceLogger() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static long deltaMillis(long previousMillis, long currentMillis) {
    return currentMillis - previousMillis;
  }

  public static void log(Logger logger, String jobId, long previousMillis,
    long currentMillis, int page, String step, int count) {

    logger.info(BULK_RENEWAL_PERF_ANALYSIS,
      "BULK_RENEWAL_PERF_ANALYSIS jobId={} nowMillis={} deltaMillis={} page={} step={} count={}",
      jobId, currentMillis, deltaMillis(previousMillis, currentMillis), page, step, count);
  }
}
