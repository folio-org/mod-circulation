package api.support.http;

import java.net.URL;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;

class TimedTaskClient {
  private final RestAssuredClient restAssuredClient;

  public TimedTaskClient(OkapiHeaders defaultHeaders) {
    this.restAssuredClient = new RestAssuredClient(defaultHeaders);
  }

  public Response start(URL url, int expectedStatusCode, String requestId) {
    return restAssuredClient
      .post(url, expectedStatusCode, requestId, 10000);
  }

  public Response attemptRun(URL url, String requestId) {
    return restAssuredClient.post(url, requestId);
  }
}
