package api;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.RestAssuredResponseConversion.toResponse;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import api.support.APITests;
import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class CirculationRulesAPITests extends APITests {
  @Test
  public void canGet() throws Exception {
    getText();
  }

  @Test
  public void canPutAndGet() throws Exception {
    String rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l no-circulation r no-hold n basic-notice o basic-overdue i basic-lost-item\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getText(), is(rule));

    rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l loan-forever r two-week-hold n two-week-notice o forever-overdue i forever-lost-item\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getText(), is(rule));
  }

  @Test
  public void canReportInvalidJson() {
    final RestAssuredClient restAssuredClient = new RestAssuredClient(
        getOkapiHeadersFromContext());

    final Response response = toResponse(restAssuredClient
      .beginRequest("bad-circulation-rules-request")
      .body("foo")
      .when().put(circulationRulesUrl())
      .then().extract().response());

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canReportValidationError() {
    JsonObject rules = new JsonObject();
    rules.put("rulesAsText", "\t");

    Response response = putRules(rules.encodePrettily());

    assertThat(response.getStatusCode(), is(422));

    JsonObject json = new JsonObject(response.getBody());

    assertThat(json.getString("message"), containsStringIgnoringCase("tab"));
    assertThat(json.getInteger("line"), is(1));
    assertThat(json.getInteger("column"), is(2));
  }

  private Response get() throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(circulationRulesUrl(), ResponseHandler.any(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return rulesAsText field */
  private String getText() throws Exception {
    Response response = get();
    assertThat("GET statusCode", response.getStatusCode(), is(200));
    String text = response.getJson().getString("rulesAsText");
    assertThat("rulesAsText field", text, is(notNullValue()));
    return text;
  }

  private Response putRules(String body) {
    final RestAssuredClient restAssuredClient = new RestAssuredClient(
      getOkapiHeadersFromContext());

    return toResponse(restAssuredClient
      .beginRequest("bad-circulation-rules-request")
      .body(body)
      .when().put(circulationRulesUrl())
      .then().extract().response());
  }
}
