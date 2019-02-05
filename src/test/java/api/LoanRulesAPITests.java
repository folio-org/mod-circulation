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

public class LoanRulesAPITests extends APITests {
  @Test
  public void canGet() throws Exception {
    getText();
  }

  @Test
  public void canPutAndGet() throws Exception {
    String rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l no-circulation\nfallback-policy: r no-hold\nfallback-policy: n basic-notice\n";

    loanRulesFixture.updateLoanRules(rule);

    assertThat(getText(), is(rule));

    rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l loan-forever\nfallback-policy: r two-week-hold\nfallback-policy: n two-week-notice\n";

    loanRulesFixture.updateLoanRules(rule);

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
    JsonObject rules = new JsonObject();
    rules.put("loanRulesAsTextFile", "\t");
    Response response = putExpectingFailure(rules);

    assertThat(response.getStatusCode(), is(422));

    JsonObject json = new JsonObject(response.getBody());

    assertThat(json.getString("message"), containsStringIgnoringCase("tab"));
    assertThat(json.getInteger("line"), is(1));
    assertThat(json.getInteger("column"), is(2));
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

  private Response putExpectingFailure(JsonObject rules) throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.loanRulesUrl(), rules, ResponseHandler.any(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }
}
