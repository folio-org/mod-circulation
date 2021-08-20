package api.support.spring.clients;

import java.net.URL;

import api.support.RestAssuredClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ScheduledJobClient {
  private final URL baseUrl;
  private final RestAssuredClient client;

  public void triggerJob() {
    client.post(baseUrl, 204, "triggering-job", null);
  }
}
