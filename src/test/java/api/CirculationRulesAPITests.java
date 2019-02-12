package api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import api.support.APITests;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class CirculationRulesAPITests extends APITests {
  @Test
  public void canGet() throws Exception {
    getText();
  }

  @Test
  public void canPutAndGet() throws Exception {
    String rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l no-circulation r no-hold n basic-notice\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getText(), is(rule));

    rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l loan-forever r two-week-hold n two-week-notice\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getText(), is(rule));
  }

  @Test
  public void canReportInvalidJson() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.circulationRulesUrl(), "foo", ResponseHandler.any(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canReportValidationError() throws Exception {
    JsonObject rules = new JsonObject();
    rules.put("rulesAsTextFile", "\t");
    Response response = putExpectingFailure(rules);

    assertThat(response.getStatusCode(), is(422));

    JsonObject json = new JsonObject(response.getBody());

    assertThat(json.getString("message"), containsStringIgnoringCase("tab"));
    assertThat(json.getInteger("line"), is(1));
    assertThat(json.getInteger("column"), is(2));
  }

  private Response get() throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(InterfaceUrls.circulationRulesUrl(), ResponseHandler.any(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return rulesAsTextFile field */
  private String getText() throws Exception {
    Response response = get();
    assertThat("GET statusCode", response.getStatusCode(), is(200));
    String text = response.getJson().getString("rulesAsTextFile");
    assertThat("rulesAsTextFile field", text, is(notNullValue()));
    return text;
  }

  private Response putExpectingFailure(JsonObject rules) throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.circulationRulesUrl(), rules, ResponseHandler.any(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }
}
