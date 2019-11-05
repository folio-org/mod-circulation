package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.RestAssuredClient.manuallyStartTimedTask;

import java.net.URL;

public class ExpiredSessionProcessingClient {

  public void runRequestExpiredSessionsProcessing(int expectedStatusCode) {
    URL url = circulationModuleUrl("/circulation/expired-session-processing");
    manuallyStartTimedTask(url, expectedStatusCode, "expired-session-processing-request");
  }
}
