package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.APITestContext.getOkapiHeadersFromContext;

import java.net.URL;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;

import api.support.http.TimedTaskClient;

public class ScheduledNoticeProcessingClient {
  private final TimedTaskClient timedTaskClient;

  public ScheduledNoticeProcessingClient() {
    timedTaskClient = new TimedTaskClient(getOkapiHeadersFromContext());
  }

  public void runDueDateNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runDueDateNoticesProcessing, mockSystemTime);
  }

  public void runDueDateNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/due-date-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "due-date-scheduled-notices-processing-request");
  }

  public void runDueDateNotRealTimeNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runDueDateNotRealTimeNoticesProcessing, mockSystemTime);
  }

  public void runDueDateNotRealTimeNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/due-date-not-real-time-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "due-date-not-real-time-scheduled-notices-processing-request");
  }

  public void runRequestNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runRequestNoticesProcessing, mockSystemTime);
  }

  public void runRequestNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/request-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "request-scheduled-notices-processing-request");
  }

  private void runWithFrozenTime(Runnable runnable, DateTime mockSystemTime) {
    try {
      DateTimeUtils.setCurrentMillisFixed(mockSystemTime.getMillis());
      runnable.run();
    } finally {
      DateTimeUtils.setCurrentMillisSystem();
    }
  }
}
