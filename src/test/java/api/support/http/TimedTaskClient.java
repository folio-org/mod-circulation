package api.support.http;

import static api.support.APITestContext.getOkapiHeadersFromContext;

import java.net.URL;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;

public class TimedTaskClient {
  public static Response manuallyStartTimedTask(URL url, int expectedStatusCode,
      String requestId) {

    return new RestAssuredClient(getOkapiHeadersFromContext())
      .post(url, expectedStatusCode, requestId, 10000);
  }
}
