package api;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.support.http.client.Response;
import org.junit.Ignore;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonObject;

public class CirculationRulesAPITests extends APITests {
  @Test
  public void canGet() {
    getRulesText();
  }

  @Test
  @Ignore
  public void canPutAndGet() {
    String rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l no-circulation r no-hold n basic-notice o basic-overdue i basic-lost-item\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getRulesText(), is(rule));

    rule = "priority: t, s, c, b, a, m, g\nfallback-policy: l loan-forever r two-week-hold n two-week-notice o forever-overdue i forever-lost-item\n";

    circulationRulesFixture.updateCirculationRules(rule);

    assertThat(getRulesText(), is(rule));
  }

  @Test
  public void canReportInvalidJson() {
    final Response response = circulationRulesFixture.putRules("foo");

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canReportValidationError() {
    JsonObject rules = new JsonObject();
    rules.put("rulesAsText", "\t");

    Response response = circulationRulesFixture.putRules(rules.encodePrettily());

    assertThat(response.getStatusCode(), is(422));

    JsonObject json = new JsonObject(response.getBody());

    assertThat(json.getString("message"), containsStringIgnoringCase("tab"));
    assertThat(json.getInteger("line"), is(1));
    assertThat(json.getInteger("column"), is(2));
  }

  /** @return rulesAsText field */
  private String getRulesText() {
    Response response = circulationRulesFixture.getRules();

    assertThat("GET statusCode", response.getStatusCode(), is(200));

    String text = response.getJson().getString("rulesAsText");
    assertThat("rulesAsText field", text, is(notNullValue()));

    return text;
  }

}
