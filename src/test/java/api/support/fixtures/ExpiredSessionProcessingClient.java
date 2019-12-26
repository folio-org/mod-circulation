package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;

import java.net.URL;

import api.support.APITestContext;
import api.support.RestAssuredClient;

public class ExpiredSessionProcessingClient {
  public void runRequestExpiredSessionsProcessing(int expectedStatusCode) {
    final RestAssuredClient restAssuredClient = new RestAssuredClient(
      APITestContext.getOkapiHeadersFromContext());

    URL url = circulationModuleUrl("/circulation/notice-session-expiration-by-timeout");

    restAssuredClient.post(url, expectedStatusCode,
      "notice-session-expiration-by-timeout-request", 10000);
  }
}
