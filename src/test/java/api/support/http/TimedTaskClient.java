package api.support.http;

import java.net.URL;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;

public class TimedTaskClient {
  private final OkapiHeaders defaultHeaders;

  public TimedTaskClient(OkapiHeaders defaultHeaders) {
    this.defaultHeaders = defaultHeaders;
  }

  public Response start(URL url, int expectedStatusCode, String requestId) {
    return new RestAssuredClient(defaultHeaders)
      .post(url, expectedStatusCode, requestId, 10000);
  }
}
