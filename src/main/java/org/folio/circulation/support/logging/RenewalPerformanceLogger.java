package org.folio.circulation.support.logging;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.resources.context.RenewalContext;

public final class RenewalPerformanceLogger {
  public static final Marker RENEWAL_PERF_ANALYSIS = MarkerManager.getMarker(
    "RENEWAL_PERF_ANALYSIS");

  private RenewalPerformanceLogger() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static long deltaMillis(long previousMillis, long currentMillis) {
    return currentMillis - previousMillis;
  }

  public static RenewalContext advance(RenewalContext context, long currentMillis) {
    return context.withLastPerformanceTimestampMillis(currentMillis);
  }

  public static void log(Logger logger, String analysisId, long previousMillis,
    long currentMillis, String loanId, String message, Object... arguments) {

    logger.info(RENEWAL_PERF_ANALYSIS,
      "RENEWAL_PERF_ANALYSIS id={} nowMillis={} deltaMillis={} loanId={} " + message,
      prepend(analysisId, currentMillis, deltaMillis(previousMillis, currentMillis),
        loanId, arguments));
  }

  public static RenewalContext logAndAdvance(Logger logger, RenewalContext context,
    long currentMillis, String message, Object... arguments) {

    log(logger, context.getPerformanceAnalysisId(),
      context.getLastPerformanceTimestampMillis(), currentMillis, loanId(context),
      message, arguments);

    return advance(context, currentMillis);
  }

  private static String loanId(RenewalContext context) {
    final Loan loan = context.getLoan();

    return loan != null ? loan.getId() : null;
  }

  private static Object[] prepend(Object first, Object second, Object third,
    Object fourth, Object[] arguments) {
    final Object[] result = new Object[arguments.length + 4];

    System.arraycopy(arguments, 0, result, 4, arguments.length);
    result[0] = first;
    result[1] = second;
    result[2] = third;
    result[3] = fourth;

    return result;
  }
}
