package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.RestAssuredClient.manuallyStartTimedTask;

import java.net.URL;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;

public class ScheduledNoticeProcessingClient {

  public void runDueDateNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runDueDateNoticesProcessing, mockSystemTime);
  }

  public void runDueDateNoticesProcessing() {
    URL url = circulationModuleUrl("/circulation/due-date-scheduled-notices-processing");
    manuallyStartTimedTask(url, 204, "due-date-scheduled-notices-processing-request");
  }

  public void runDueDateNotRealTimeNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runDueDateNotRealTimeNoticesProcessing, mockSystemTime);
  }

  public void runDueDateNotRealTimeNoticesProcessing() {
    URL url = circulationModuleUrl("/circulation/due-date-not-real-time-scheduled-notices-processing");
    manuallyStartTimedTask(url, 204, "due-date-not-real-time-scheduled-notices-processing-request");
  }

  public void runRequestNoticesProcessing(DateTime mockSystemTime) {
    runWithFrozenTime(this::runRequestNoticesProcessing, mockSystemTime);
  }

  public void runRequestNoticesProcessing() {
    URL url = circulationModuleUrl("/circulation/request-scheduled-notices-processing");
    manuallyStartTimedTask(url, 204, "request-scheduled-notices-processing-request");
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
