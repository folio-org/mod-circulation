package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanRulesAPITests {

  OkapiHttpClient client = APITestSuite.createClient(exception -> {
    System.out.println(
      String.format("Request to circulation module failed: %s",
        exception.toString()));
  });

  @Test
  public void canPutAndGet() throws Exception {
    Response response = put("");
    assertThat(response.getStatusCode(), is(204));

    assertThat(getText(), is(""));

    String rule = "# some comment\nfallback-policy: no-circulation\n";

    response = put(rule);
    assertThat(response.getStatusCode(), is(204));

    assertThat(getText(), is(rule));
  }

  private static URL loanRulesURL() {
    return APITestSuite.circulationModuleUrl("/circulation/loan-rules");
  }

  private Response get() throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(loanRulesURL(), ResponseHandler.any(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return loanRulesAsTextFile field */
  private String getText() throws Exception {
    Response response = get();
    assertThat("GET statusCode", response.getStatusCode(), is(200));
    return response.getJson().getString("loanRulesAsTextFile");
  }

  private Response put(String rulesAsText) throws Exception {
    JsonObject rules = new JsonObject();
    rules.put("loanRulesAsTextFile", rulesAsText);
    return put(rules);
  }

  private Response put(JsonObject rules) throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(loanRulesURL(), rules, ResponseHandler.any(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }
}
