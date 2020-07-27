package api.support.spring.clients;

import java.net.URL;

import org.folio.circulation.support.http.client.Response;
import org.junit.Assert;

import api.support.RestAssuredClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ScheduledJobClient {
  private final URL baseUrl;
  private final RestAssuredClient client;

  public void triggerJob() {
    final Response post = client.post(null, baseUrl, "triggering-job");

    if (post.getStatusCode() != 200 || post.getStatusCode() != 204) {
      Assert.fail("Status code is not success: " + post.getStatusCode());
    }
  }
}
