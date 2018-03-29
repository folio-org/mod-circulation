package api;

import io.vertx.core.json.JsonObject;

import api.support.http.InterfaceUrls;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public class LoanRulesAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  @Test
  public void canGet() throws Exception {
    getText();
  }

  @Test
  public void canPutAndGet() throws Exception {
    Response response = put("");
    assertThat(response.getStatusCode(), is(204));

    assertThat(getText(), is(""));

    String rule = "fallback-policy: no-circulation\n";

    response = put(rule);
    assertThat(response.getStatusCode(), is(204));

    assertThat(getText(), is(rule));
  }

  @Test
  public void canReportInvalidJson() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.loanRulesUrl(), "foo", ResponseHandler.any(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canReportValidationError() throws Exception {
    Response response = put(" \t");
    assertThat(response.getStatusCode(), is(422));
    JsonObject json = new JsonObject(response.getBody());
    assertThat(json.getString("message"), containsString("tab"));
    assertThat(json.getInteger("line"), is(0));
    assertThat(json.getInteger("column"), is(1));
  }

  private Response get() throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(InterfaceUrls.loanRulesUrl(), ResponseHandler.any(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return loanRulesAsTextFile field */
  private String getText() throws Exception {
    Response response = get();
    assertThat("GET statusCode", response.getStatusCode(), is(200));
    String text = response.getJson().getString("loanRulesAsTextFile");
    assertThat("loanRulesAsTextFile field", text, is(notNullValue()));
    return text;
  }

  private Response put(String rulesAsText) throws Exception {
    JsonObject rules = new JsonObject();
    rules.put("loanRulesAsTextFile", rulesAsText);
    return put(rules);
  }

  private Response put(JsonObject rules) throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.loanRulesUrl(), rules, ResponseHandler.any(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }
}
