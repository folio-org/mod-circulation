package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.RestAssuredClient.manuallyStartTimedTask;

import java.net.URL;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;

public class ScheduledNoticeProcessingClient {

  public void runNoticesProcessing(DateTime mockSystemTime) {
    DateTimeUtils.setCurrentMillisFixed(mockSystemTime.getMillis());
    runNoticesProcessing();
    DateTimeUtils.setCurrentMillisSystem();
  }

  public void runNoticesProcessing() {
    URL url = circulationModuleUrl("/circulation/scheduled-notices-processing");
    manuallyStartTimedTask(url, 204, "scheduled-notices-processing-request");
  }
}
