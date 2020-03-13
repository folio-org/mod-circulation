package api;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.Ignore;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonObject;

public class CirculationRulesAPITests extends APITests {

  private static final String CIRCULATION_RULE_TEMPLATE =
    "priority: t, s, c, b, a, m, g\nfallback-policy: l %s r %s n %s o %s i %s \n";

  @Test
  public void canGet() {
    getRulesText();
  }

  @Test
  public void canPutAndGet() {
    UUID lp1 = UUID.randomUUID();
    UUID lp2 = UUID.randomUUID();
    UUID rp1 = UUID.randomUUID();
    UUID rp2 = UUID.randomUUID();
    UUID np1 = UUID.randomUUID();
    UUID np2 = UUID.randomUUID();
    UUID op1 = UUID.randomUUID();
    UUID op2 = UUID.randomUUID();
    UUID ip1 = UUID.randomUUID();
    UUID ip2 = UUID.randomUUID();

    String rule = String.format(CIRCULATION_RULE_TEMPLATE, lp1, rp1, np1, op1, ip1);
    setRules(rule);

    assertThat(getRulesText(), is(rule));

    rule = String.format(CIRCULATION_RULE_TEMPLATE, lp2, rp2, np2, op2, ip2);
    setRules(rule);

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

  private void setRules(String rules) {
    createPoliciesIfDoNotExist(rules);
    circulationRulesFixture.updateCirculationRules(rules);
  }

  private void createPoliciesIfDoNotExist(String rules) {
    loanPoliciesFixture.create(getPolicyFromRule(rules, "l"));
    noticePoliciesFixture.create(getPolicyFromRule(rules, "n"));
    requestPoliciesFixture.allowAllRequestPolicy(getPolicyFromRule(rules, "r"));
    overdueFinePoliciesFixture.create(getPolicyFromRule(rules, "o"));
    lostItemFeePoliciesFixture.create(getPolicyFromRule(rules, "i"));
  }
}
