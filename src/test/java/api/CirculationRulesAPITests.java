package api;

import static api.support.APITestContext.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;

class CirculationRulesAPITests extends APITests {

  private static final String CIRCULATION_RULE_TEMPLATE =
    "priority: t, s, c, b, a, m, g\nfallback-policy: l %s r %s n %s o %s i %s \n";

  @Test
  void canGet() {
    getRulesText();
  }

  @Test
  void canPutAndGet() {
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

    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(lp1)
      .withName("Example LoanPolicy " + lp1));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(np1)
      .withName("Example NoticePolicy " + np1));
    requestPoliciesFixture.allowAllRequestPolicy(rp1);
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(op1)
      .withName("Example OverdueFinePolicy " + op1));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(ip1)
      .withName("Example lostItemPolicy " + ip1));

    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(lp2)
      .withName("Example LoanPolicy " + lp2));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(np2)
      .withName("Example NoticePolicy " + np2));
    requestPoliciesFixture.allowAllRequestPolicy(rp2);
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(op2)
      .withName("Example OverdueFinePolicy " + op2));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(ip2)
      .withName("Example lostItemPolicy " + ip2));

    String rule = String.format(CIRCULATION_RULE_TEMPLATE, lp1, rp1, np1, op1, ip1);
    setRules(rule);

    assertThat(getRulesText(), is(rule));

    rule = String.format(CIRCULATION_RULE_TEMPLATE, lp2, rp2, np2, op2, ip2);
    setRules(rule);

    assertThat(getRulesText(), is(rule));
  }

  @Test
  void canDefineFallbackPoliciesInAnyOrder() {

    UUID loanPolicyId = UUID.randomUUID();
    UUID requestPolicyId = UUID.randomUUID();
    UUID noticePolicyId = UUID.randomUUID();
    UUID overdueFinePolicyId = UUID.randomUUID();
    UUID lostItemFeePolicyId = UUID.randomUUID();

    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(loanPolicyId)
      .withName("Example LoanPolicy " + loanPolicyId));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(noticePolicyId)
      .withName("Example NoticePolicy " + noticePolicyId));
    requestPoliciesFixture.allowAllRequestPolicy(requestPolicyId);
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(overdueFinePolicyId)
      .withName("Example OverdueFinePolicy " + overdueFinePolicyId));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(lostItemFeePolicyId)
      .withName("Example lostItemPolicy " + lostItemFeePolicyId));

    String fallbackRuleInDiffPolicyOrder1 = "priority: t, s, c, b, a, m, g\n" +
      "fallback-policy: i %s o %s n %s r %s l %s \n";
    String fallbackRuleInDiffPolicyOrder2 = "priority: t, s, c, b, a, m, g\n" +
      "fallback-policy: o %s i %s r %s n %s l %s \n";
    String fallbackRuleInDiffPolicyOrder3 = "priority: t, s, c, b, a, m, g\n" +
      "fallback-policy: o %s i %s l %s n %s r %s \n";
    String fallbackRuleInDiffPolicyOrder4 = "priority: t, s, c, b, a, m, g\n" +
      "fallback-policy: r %s l %s i %s o %s n %s \n";
    String fallbackRuleInDiffPolicyOrder5 = "priority: t, s, c, b, a, m, g\n" +
      "fallback-policy: o %s i %s l %s n %s r %s \n";

    String rule = String.format(fallbackRuleInDiffPolicyOrder1, lostItemFeePolicyId,
      overdueFinePolicyId, noticePolicyId, requestPolicyId, loanPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(fallbackRuleInDiffPolicyOrder2, overdueFinePolicyId,
      lostItemFeePolicyId, requestPolicyId, noticePolicyId, loanPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(fallbackRuleInDiffPolicyOrder3, overdueFinePolicyId,
      lostItemFeePolicyId, loanPolicyId, noticePolicyId, requestPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(fallbackRuleInDiffPolicyOrder4, requestPolicyId, loanPolicyId,
      lostItemFeePolicyId, overdueFinePolicyId, noticePolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(fallbackRuleInDiffPolicyOrder5, overdueFinePolicyId,
      lostItemFeePolicyId, loanPolicyId, noticePolicyId, requestPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));
  }

  @Test
  void canDefinePoliciesForARuleInAnOrder() {

    UUID loanPolicyId = UUID.randomUUID();
    UUID requestPolicyId = UUID.randomUUID();
    UUID noticePolicyId = UUID.randomUUID();
    UUID overdueFinePolicyId = UUID.randomUUID();
    UUID lostItemFeePolicyId = UUID.randomUUID();

    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(loanPolicyId)
      .withName("Example LoanPolicy " + loanPolicyId));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(noticePolicyId)
      .withName("Example NoticePolicy " + noticePolicyId));
    requestPoliciesFixture.allowAllRequestPolicy(requestPolicyId);
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(overdueFinePolicyId)
      .withName("Example OverdueFinePolicy " + overdueFinePolicyId));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(lostItemFeePolicyId)
      .withName("Example lostItemPolicy " + lostItemFeePolicyId));

    String fallbackPolicyRule = String.format(CIRCULATION_RULE_TEMPLATE,
      loanPolicyId, requestPolicyId, noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);

    String regularRuleInDiffPolicyOrder1 = fallbackPolicyRule +
      "m book: i %s o %s n %s r %s l %s \n";
    String regularRuleInDiffPolicyOrder2 = fallbackPolicyRule +
      "m book: o %s i %s r %s n %s l %s \n";
    String regularRuleInDiffPolicyOrder3 = fallbackPolicyRule +
      "m book: o %s i %s l %s n %s r %s \n";
    String regularRuleInDiffPolicyOrder4 = fallbackPolicyRule +
      "m book: r %s l %s i %s o %s n %s \n";
    String regularRuleInDiffPolicyOrder5 = fallbackPolicyRule +
      "m book: o %s i %s l %s n %s r %s \n";

    String rule = String.format(regularRuleInDiffPolicyOrder1, lostItemFeePolicyId,
      overdueFinePolicyId, noticePolicyId, requestPolicyId, loanPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(regularRuleInDiffPolicyOrder2, overdueFinePolicyId,
      lostItemFeePolicyId, requestPolicyId, noticePolicyId, loanPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(regularRuleInDiffPolicyOrder3, overdueFinePolicyId,
      lostItemFeePolicyId, loanPolicyId, noticePolicyId, requestPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(regularRuleInDiffPolicyOrder4, requestPolicyId, loanPolicyId,
      lostItemFeePolicyId, overdueFinePolicyId, noticePolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));

    rule = String.format(regularRuleInDiffPolicyOrder5, overdueFinePolicyId,
      lostItemFeePolicyId, loanPolicyId, noticePolicyId, requestPolicyId);
    setRules(rule);
    assertThat(getRulesText(), is(rule));
  }

  @Test
  void cannotUpdateCirculationRulesWithInvalidLoanPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      UUID.randomUUID(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getString("message"),
      is("The policy l does not exist"));
  }

  @Test
  void cannotUpdateCirculationRulesWithInvalidNoticePolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      UUID.randomUUID(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getString("message"),
      is("The policy n does not exist"));
  }

  @Test
  void cannotUpdateCirculationRulesWithInvalidRequestPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId(),
      UUID.randomUUID(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getString("message"),
      is("The policy r does not exist"));
  }

  @Test
  void cannotUpdateCirculationRulesWithOverdueFinePolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      UUID.randomUUID(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    Response response = circulationRulesFixture
      .attemptUpdateCirculationRules(rule);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getString("message"),
      is("The policy o does not exist"));
  }

  @Test
  void cannotUpdateCirculationRulesWithLostItemPolicyId() {

    String rule = circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      UUID.randomUUID());

    Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getString("message"),
      is("The policy i does not exist"));
  }

  @Test
  void canUpdateCirculationRulesWithTwentyExistingLoanPolicies() {

    Set<UUID> loanPolicyIds = getSetOfPolicyIds(20);
    createLoanPolicies(loanPolicyIds);

    loanPolicyIds.forEach(loanPolicyId -> {
      String rule = circulationRulesFixture.soleFallbackPolicyRule(
        loanPolicyId, requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePoliciesFixture.activeNotice().getId(),
        overdueFinePoliciesFixture.facultyStandard().getId(),
        lostItemFeePoliciesFixture.facultyStandard().getId());

      Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

      assertThat(String.format(
        "Failed to set circulation rules: %s", response.getBody()),
        response.getStatusCode(), is(204));
    });
  }

  @Test
  void canUpdateCirculationRulesWithTwentyExistingNoticePolicies() {

    Set<UUID> noticePolicyIds = getSetOfPolicyIds(20);
    createNoticePolicies(noticePolicyIds);

    noticePolicyIds.forEach(noticePolicyId -> {
      String rule = circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateFixed().getId(),
        requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePolicyId,
        overdueFinePoliciesFixture.facultyStandard().getId(),
        lostItemFeePoliciesFixture.facultyStandard().getId());

      Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

      assertThat(String.format(
        "Failed to set circulation rules: %s", response.getBody()),
        response.getStatusCode(), is(204));
    });
  }

  @Test
  void canUpdateCirculationRulesWithTwentyExistingRequestPolicies() {

    Set<UUID> requestPolicyIds = getSetOfPolicyIds(20);
    requestPolicyIds.forEach(requestPoliciesFixture::allowAllRequestPolicy);

    requestPolicyIds.forEach(requestPolicyId -> {
      UUID allowAllRequestPolicyId = requestPoliciesFixture.allowAllRequestPolicy(
        requestPolicyId).getId();
      String rule = circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateFixed().getId(),
        allowAllRequestPolicyId,
        noticePoliciesFixture.activeNotice().getId(),
        overdueFinePoliciesFixture.facultyStandard().getId(),
        lostItemFeePoliciesFixture.facultyStandard().getId());

      Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

      assertThat(String.format(
        "Failed to set circulation rules: %s", response.getBody()),
        response.getStatusCode(), is(204));
    });
  }

  @Test
  void canUpdateCirculationRulesWithTwentyExistingOverdueFinePolicies() {

    Set<UUID> overdueFinePolicyIds = getSetOfPolicyIds(20);
    createOverdueFinePolicies(overdueFinePolicyIds);

    overdueFinePolicyIds.forEach(overdueFinePolicyId -> {
      String rule = circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateFixed().getId(),
        requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePoliciesFixture.activeNotice().getId(),
        overdueFinePolicyId,
        lostItemFeePoliciesFixture.facultyStandard().getId());

      Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

      assertThat(String.format(
        "Failed to set circulation rules: %s", response.getBody()),
        response.getStatusCode(), is(204));
    });
  }

  @Test
  void canUpdateCirculationRulesWithTwentyExistingLostItemFeePolicies() {

    Set<UUID> lostItemFeePolicyIds = getSetOfPolicyIds(20);
    createLostItemFeePolicies(lostItemFeePolicyIds);

    lostItemFeePolicyIds.forEach(lostItemFeePolicyId -> {
      String rule = circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateFixed().getId(),
        requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePoliciesFixture.activeNotice().getId(),
        overdueFinePoliciesFixture.facultyStandard().getId(),
        lostItemFeePolicyId);

      Response response = circulationRulesFixture.attemptUpdateCirculationRules(rule);

      assertThat(String.format(
        "Failed to set circulation rules: %s", response.getBody()),
        response.getStatusCode(), is(204));
    });
  }

  @Test
  void canReportInvalidJson() {
    final Response response = circulationRulesFixture.putRules("foo");

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  void canReportValidationError() {
    JsonObject rules = new JsonObject();
    rules.put("rulesAsText", "\t");

    Response response = circulationRulesFixture.putRules(rules.encodePrettily());

    assertThat(response.getStatusCode(), is(422));

    JsonObject json = new JsonObject(response.getBody());

    assertThat(json.getString("message"),
      is("Tabulator character is not allowed, use spaces instead."));
    assertThat(json.getInteger("line"), is(1));
    assertThat(json.getInteger("column"), is(2));
  }

  @Test
  void getRefreshesCirculationRulesCache() {
    CirculationRulesCache cache = CirculationRulesCache.getInstance();
    cache.dropCache();
    assertThat(cache.getRules(TENANT_ID), nullValue());
    String rules = circulationRulesFixture.getCirculationRules();
    assertThat(cache.getRules(TENANT_ID).getRulesAsText(), equalTo(rules));
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

  private Set<UUID> getSetOfPolicyIds(int numberOfPolicies) {
    Set<UUID> ids = new HashSet<>();
    for (int i = 0; i < numberOfPolicies; i++) {
      ids.add(UUID.randomUUID());
    }
    return ids;
  }

  private void createLoanPolicies(Set<UUID> ids) {
    ids.forEach(id -> loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withId(id)
        .withName("Example LoanPolicy " + id)));
  }

  private void createNoticePolicies(Set<UUID> ids) {
    ids.forEach(id -> noticePoliciesFixture.create(
      new NoticePolicyBuilder()
        .withId(id)
        .withName("Example NoticePolicy " + id)));
  }

  private void createOverdueFinePolicies(Set<UUID> ids) {
    ids.forEach(id -> overdueFinePoliciesFixture.create(
      new OverdueFinePolicyBuilder()
        .withId(id)
        .withName("Example OverdueFinePolicy " + id)));
  }

  private void createLostItemFeePolicies(Set<UUID> ids) {
    ids.forEach(id -> lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withId(id)
        .withName("Example LostItemFeePolicy " + id)));
  }
}
