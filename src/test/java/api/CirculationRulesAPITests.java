package api;

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
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

    loanPoliciesFixture.create(lp1);
    noticePoliciesFixture.create(np1);
    requestPoliciesFixture.allowAllRequestPolicy(rp1);
    overdueFinePoliciesFixture.create(op1);
    lostItemFeePoliciesFixture.create(ip1);
    loanPoliciesFixture.create(lp2);
    noticePoliciesFixture.create(np2);
    requestPoliciesFixture.allowAllRequestPolicy(rp2);
    overdueFinePoliciesFixture.create(op2);
    lostItemFeePoliciesFixture.create(ip2);

    String rule = String.format(CIRCULATION_RULE_TEMPLATE, lp1, rp1, np1, op1, ip1);
    setRules(rule);

    assertThat(getRulesText(), is(rule));

    rule = String.format(CIRCULATION_RULE_TEMPLATE, lp2, rp2, np2, op2, ip2);
    setRules(rule);

    assertThat(getRulesText(), is(rule));
  }

  @Test
  public void cannotUpdateCirculationRulesWithInvalidLoanPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      UUID.randomUUID().toString(),
      requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
      noticePoliciesFixture.activeNotice().getId().toString(),
      overdueFinePoliciesFixture.facultyStandard().getId().toString(),
      lostItemFeePoliciesFixture.facultyStandard().getId().toString());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule, "l");
    assertThat("The policy l does not exist",
      response.getStatusCode(), Is.is(422));
  }

  @Test
  public void cannotUpdateCirculationRulesWithInvalidNoticePolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId().toString(),
      requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
      UUID.randomUUID().toString(),
      overdueFinePoliciesFixture.facultyStandard().getId().toString(),
      lostItemFeePoliciesFixture.facultyStandard().getId().toString());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule, "n");
    assertThat("The policy n does not exist",
      response.getStatusCode(), Is.is(422));
  }

  @Test
  public void cannotUpdateCirculationRulesWithInvalidRequestPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId().toString(),
      UUID.randomUUID().toString(),
      noticePoliciesFixture.activeNotice().getId().toString(),
      overdueFinePoliciesFixture.facultyStandard().getId().toString(),
      lostItemFeePoliciesFixture.facultyStandard().getId().toString());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule, "r");
    assertThat("The policy r does not exist",
      response.getStatusCode(), Is.is(422));
  }

  @Test
  public void cannotUpdateCirculationRulesWithOverdueFinePolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId().toString(),
      requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
      noticePoliciesFixture.activeNotice().getId().toString(),
      UUID.randomUUID().toString(),
      lostItemFeePoliciesFixture.facultyStandard().getId().toString());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule, "o");
    assertThat("The policy o does not exist",
      response.getStatusCode(), Is.is(422));
  }

  @Test
  public void cannotUpdateCirculationRulesWithLostItemPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId().toString(),
      requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
      noticePoliciesFixture.activeNotice().getId().toString(),
      overdueFinePoliciesFixture.facultyStandard().getId().toString(),
      UUID.randomUUID().toString());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule, "i");
    assertThat("The policy i does not exist",
      response.getStatusCode(), Is.is(422));
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
    circulationRulesFixture.updateCirculationRules(rules);
  }
}
