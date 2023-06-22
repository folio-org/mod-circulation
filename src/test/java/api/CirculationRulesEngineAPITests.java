package api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

import java.util.UUID;

import org.folio.circulation.rules.Campus;
import org.folio.circulation.rules.Institution;
import org.folio.circulation.rules.ItemLocation;
import org.folio.circulation.rules.ItemType;
import org.folio.circulation.rules.Library;
import org.folio.circulation.rules.LoanType;
import org.folio.circulation.rules.PatronGroup;
import org.folio.circulation.rules.Policy;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class CirculationRulesEngineAPITests extends APITests {
  public CirculationRulesEngineAPITests() {
    super(false, false);
  }

  private void setRules(String rules) {
    circulationRulesFixture.updateCirculationRules(rules);
  }

  private final IndividualResource mainFloor = locationsFixture.mainFloor();
  private final IndividualResource fourthFloor = locationsFixture.fourthFloor();

  //Test data to work with our defined values
  private ItemType m1 = new ItemType("96d4bdf1-5fc2-40ef-9ace-6d7e3e48ec4d");
  private ItemType m2 = new ItemType("b6375fcb-caaf-4b94-944d-b1a6bb589425");
  private LoanType t1 = new LoanType("2e6f51b9-d00a-4f1d-9960-49b1977acfca");
  private LoanType t2 = new LoanType("220e2dad-e3c7-42f3-bb46-515ba29ba65f");
  private PatronGroup g1 = new PatronGroup("0122feae-bd0e-4405-88de-525d93ba7cfd");
  private PatronGroup g2 = new PatronGroup("87d14197-6de3-4ba5-9201-6c4129504adf");
  private ItemLocation s1 = new ItemLocation(String.valueOf(mainFloor.getId()));
  private ItemLocation s2 = new ItemLocation(String.valueOf(fourthFloor.getId()));
  private Institution i1 = new Institution(mainFloor.getJson().getString("institutionId"));
  private Library l1 = new Library(mainFloor.getJson().getString("libraryId"));
  private Campus c1 = new Campus(mainFloor.getJson().getString("campusId"));
  // Loan Policies
  private Policy lp6 = new Policy("6a475259-8a97-4992-a415-76440f5f7c23");
  private Policy lp7 = new Policy("7b586360-8ba8-4aa3-b526-875510608d34");
  private Policy lp1 = new Policy("f6f88da8-2aaf-48c7-944e-0de3f4cc2368");
  private Policy lp2 = new Policy("c42e3a01-eb61-4edd-8cb0-8c7ecc0b4ca2");
  private Policy lp3 = new Policy("f0c8d755-0e56-4d38-9a45-9cd9248b1ae8");
  private Policy lp4 = new Policy("0122feae-bd0e-4405-88de-525d93ba7cfd");
  // Request Policies
  private Policy rp1 = new Policy("52069c78-75f6-4de5-bec0-85b87fbf9414");
  private Policy rp2 = new Policy("921d07d7-2869-4add-9cdd-98a1c4b9de64");
  // Notice Policies
  private Policy np1 = new Policy("662cb440-c66b-46ba-b70f-7ebff4026644");
  private Policy np2 = new Policy("b6bcddac-7c6c-4b29-a6fe-4627ef78c782");
  // Overdue Fine Policies
  private Policy op1 = new Policy("eadea55e-0e6f-4d77-a1b3-0a434a1bf4a9");
  private Policy op2 = new Policy("ee672000-30f6-4f49-9311-b6a1fdf21726");
  // Lost Item Fee Policies
  private Policy lip1 = new Policy("314b5112-284a-4552-973e-a5618f510fa9");
  private Policy lip2 = new Policy("43ea9663-e6d9-4505-9951-9662f06c4127");

  private String rulesFallback =  "priority: t, s, c, b, a, m, g\nfallback-policy: l " + lp6 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1;
  private String rulesFallback2 = "priority: t, s, c, b, a, m, g\nfallback-policy: l " + lp7 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2;

  private String rules1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + lp2 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
      "m " + m2 + ": l " + lp3 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
      "    g " + g2 + ": l " + lp4 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1
      );

  private String rules2 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + lp6 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
      "m " + m1 + ": l " + lp1 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
      "m " + m1 + " + t " + t1 + " : l " + lp2 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
      "m " + m1 + " + t " + t1 + " + g " + g1 + " : l " + lp3 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1
      );

  private String rulesWithInstitution = String.join("\n",
    "priority: t, s, c, b, a, m, g",
    "fallback-policy: l " + lp2 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
    "m " + m2 + ": l " + lp3 + " r " + rp2 + " n " + np2 + " o " + op1 + " i " + lip1,
    "a " + i1 + ": l " + lp4 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2
  );

  private String rulesWithLibrary = String.join("\n",
    "priority: t, s, c, b, a, m, g",
    "fallback-policy: l " + lp2 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
    "m " + m2 + ": l " + lp3 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2,
    "c " + l1 + ": l " + lp4 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2
  );

  private String rulesWithCampus = String.join("\n",
    "priority: t, s, c, b, a, m, g",
    "fallback-policy: l " + lp2 + " r " + rp1 + " n " + np1 + " o " + op1 + " i " + lip1,
    "m " + m2 + ": l " + lp3 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2,
    "b " + c1 + ": l " + lp4 + " r " + rp2 + " n " + np2 + " o " + op2 + " i " + lip2
  );

  @BeforeEach
  public void setUp() {
    CirculationRulesCache.getInstance().dropCache();
    setPoliciesIdsToTheFixture();
  }

  private void setPoliciesIdsToTheFixture() {
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp1.toString()))
      .withName("Example LoanPolicy " + lp1));
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp2.toString()))
      .withName("Example LoanPolicy " + lp2));
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp3.toString()))
      .withName("Example LoanPolicy " + lp3));
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp4.toString()))
      .withName("Example LoanPolicy " + lp4));
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp6.toString()))
      .withName("Example LoanPolicy " + lp6));
    loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.fromString(lp7.toString()))
      .withName("Example LoanPolicy " + lp7));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(UUID.fromString(np1.toString()))
      .withName("Example NoticePolicy " + np1));
    noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(UUID.fromString(np2.toString()))
      .withName("Example NoticePolicy " + np2));
    requestPoliciesFixture.allowAllRequestPolicy(UUID.fromString(rp1.toString()));
    requestPoliciesFixture.allowAllRequestPolicy(UUID.fromString(rp2.toString()));
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(UUID.fromString(op1.toString()))
      .withName("Example OverdueFinePolicy " + op1));
    overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
      .withId(UUID.fromString(op2.toString()))
      .withName("Example OverdueFinePolicy " + op2));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(UUID.fromString(lip1.toString()))
      .withName("Example lostItemPolicy " + lip1));
    lostItemFeePoliciesFixture.create(new LostItemFeePolicyBuilder()
      .withId(UUID.fromString(lip2.toString()))
      .withName("Example lostItemPolicy " + lip2));
  }

  @Test
  void applyLoanWithoutParameters() {
    final Response response = circulationRulesFixture
      .attemptToApplyRulesWithNoParameters("/loan-policy");

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  void applyRequestWithoutParameters() {
    final Response response = circulationRulesFixture
      .attemptToApplyRulesWithNoParameters("/request-policy");

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  void applyNoticeWithoutParameters() {
    final Response response = circulationRulesFixture
      .attemptToApplyRulesWithNoParameters("/notice-policy");

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  void applyOneLoanParameterMissing() {
    applyRulesWithMissingParameters("loan", null, t1.id, lp1.id, s1.id, "item_type_id");
    applyRulesWithMissingParameters("loan", m1.id, null, lp1.id, s1.id, "loan_type_id");
    applyRulesWithMissingParameters("loan", m1.id, t1.id, null, s1.id, "patron_type_id");
    applyRulesWithMissingParameters("loan", m1.id, t1.id, lp1.id, null, "location_id");
  }

  @Test
  void applyOneRequestParameterMissing() {
    applyRulesWithMissingParameters("request", null, t1.id, lp1.id, s1.id, "item_type_id");
    applyRulesWithMissingParameters("request", m1.id, null, lp1.id, s1.id, "loan_type_id");
    applyRulesWithMissingParameters("request", m1.id, t1.id, null, s1.id, "patron_type_id");
    applyRulesWithMissingParameters("request", m1.id, t1.id, lp1.id, null, "location_id");
  }

  @Test
  void applyOneNoticeParameterMissing() {
    applyRulesWithMissingParameters("notice", null, t1.id, lp1.id, s1.id, "item_type_id");
    applyRulesWithMissingParameters("notice", m1.id, null, lp1.id, s1.id, "loan_type_id");
    applyRulesWithMissingParameters("notice", m1.id, t1.id, null, s1.id, "patron_type_id");
    applyRulesWithMissingParameters("notice", m1.id, t1.id, lp1.id, null, "location_id");
  }

  @Test
  void applyInvalidUuid() {
    applyInvalidUuid("");
    applyInvalidUuid("0");
    applyInvalidUuid("f");
    applyInvalidUuid("-");
    applyInvalidUuid("0000000-0000-1000-8000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-00000000000");
    applyInvalidUuid("000000000-0000-1000-8000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-0000000000000");
    applyInvalidUuid("00000000-0000-0000-8000-000000000000");
    applyInvalidUuid("g0000000-0000-1000-0000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-00000000000g");
    applyInvalidUuid("00000000000010008000000000000000");
  }

  private void applyInvalidUuid(String uuid) {
    applyInvalidUuid(uuid, t1.id, lp1.id, s1.id, "loan");
    applyInvalidUuid(m1.id,  uuid, lp1.id, s1.id, "loan");
    applyInvalidUuid(m1.id, t1.id,  uuid,  s1.id, "loan");
    applyInvalidUuid(m1.id, t1.id, lp1.id,  uuid, "loan");

    applyInvalidUuid( uuid, t1.id, lp1.id, s1.id, "request");
    applyInvalidUuid(m1.id,  uuid, lp1.id, s1.id, "request");
    applyInvalidUuid(m1.id, t1.id,  uuid,  s1.id, "request");
    applyInvalidUuid(m1.id, t1.id, lp1.id,  uuid, "request");
  }

  private void applyInvalidUuid(String itemType, String loanType,
    String patronGroup, String location, String type) {

    final Response response = circulationRulesFixture
      .attemptToApplyRulesWithInvalidParameters(type,
        itemType, loanType, patronGroup, location);

    assertThat(response.getBody(), containsString("uuid"));
  }

  @Test
  void loanFallback() {
    setRules(rulesFallback);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp6));
  }

  @Test
  void requestFallback() {
    setRules(rulesFallback);
    assertThat(applyRequestPolicy(m1, t1, g1, s1), is(rp1));
  }

  @Test
  void noticeFallback() {
    setRules(rulesFallback);
    assertThat(applyNoticePolicy(m1, t1, g1, s1), is(np1));
  }

  @Test
  void test1() {
    setRules(rules1);
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s2), is(lp4));
    assertThat(applyRulesForLoanPolicy(m2, t2, g1, s2), is(lp3));
    assertThat(applyRulesForLoanPolicy(m1, t2, g1, s2), is(lp2));
  }

  @Test
  void test2() {
    setRules(rules2);
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s2), is(lp6));
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s2), is(lp1));
    assertThat(applyRulesForLoanPolicy(m1, t1, g2, s2), is(lp2));
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s2), is(lp3));
  }

  @Test
  void shouldApplyRulesWithInstitution() {
    setRules(rulesWithInstitution);
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s2), is(lp2));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s2), is(lp3));
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s1), is(lp4));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s1), is(lp4));
  }

  @Test
  void shouldApplyRulesWithLibrary() {
    setRules(rulesWithLibrary);
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s2), is(lp2));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s2), is(lp3));
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s1), is(lp4));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s1), is(lp4));
  }

  @Test
  void shouldApplyRulesWithCampus() {
    setRules(rulesWithCampus);
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s2), is(lp2));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s2), is(lp3));
    assertThat(applyRulesForLoanPolicy(m1, t2, g2, s1), is(lp4));
    assertThat(applyRulesForLoanPolicy(m2, t2, g2, s1), is(lp4));
  }

  @Test
  void canDetermineAllLoanPolicyMatches() {
    setRules(rules1);

    JsonArray matches = circulationRulesFixture.applyAllRulesForLoanPolicy(
        m2, t2, g2, s2);

    assertThat(matches.size(), is(3));

    matchesLoanPolicy(matches, 0, lp4, 4);
    matchesLoanPolicy(matches, 1, lp3, 3);
    matchesLoanPolicy(matches, 2, lp2, 2);
  }

  @Test
  void canDetermineAllRequestPolicyMatches() {
    setRules(rules1);

    JsonArray matches = circulationRulesFixture.applyAllRulesForRequestPolicy(
      m2, t2, g2, s2);

    assertThat(matches.size(), is(3));

    matchesRequestPolicy(matches, 0, rp1, 4);
    matchesRequestPolicy(matches, 1, rp1, 3);
    matchesRequestPolicy(matches, 2, rp1, 2);
  }

  @Test
  void canDetermineAllPatronNoticePolicyMatches() {
    setRules(rules1);

    JsonArray matches = circulationRulesFixture.applyAllRulesForNoticePolicy(
      m2, t2, g2, s2);

    assertThat(matches.size(), is(3));

    matchesNoticePolicy(matches, 0, np1, 4);
    matchesNoticePolicy(matches, 1, np1, 3);
    matchesNoticePolicy(matches, 2, np1, 2);
  }

  @Test
  void rulesEvaluationPassesWhenTheProvidedLocationDoesNotExist() {
    // The underlying rules are irrelevant
    setRules(rulesWithInstitution);

    final var itemType = m2;
    final var loanType = new LoanType(UUID.randomUUID().toString());
    final var patronGroup = new PatronGroup(UUID.randomUUID().toString());
    final var locationThatDoesNotExist = new ItemLocation(UUID.randomUUID().toString());

    assertThat(applyRequestPolicy(itemType, loanType, patronGroup, locationThatDoesNotExist), is(rp2));
  }

  @Test
  void setRulesInvalidatesCache() {
    setRules(rulesFallback);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp6));
    setRules(rulesFallback2);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp7));
    setRules(rulesFallback);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp6));
    setRules(rulesFallback2);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp7));
  }

  @Test
  void cachedRulesAreUsedEvenWhenRulesInStorageHaveBeenChanged() {
    setRules(rulesFallback);
    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp6));

    circulationRulesFixture.updateCirculationRulesWithoutInvalidatingCache(
      rulesFallback2);

    assertThat(applyRulesForLoanPolicy(m1, t1, g1, s1), is(lp6));
  }

  @Test
  void canRefreshRules() {
    Response response = circulationRulesFixture.attemptRefreshRules();
    assertThat(response.getStatusCode(), is(204));
  }

  private Policy applyRulesForLoanPolicy(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ItemLocation location) {

    return circulationRulesFixture.applyRulesForLoanPolicy(
        itemType, loanType, patronGroup, location);
  }

  private Policy applyRequestPolicy(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ItemLocation location) {

    return circulationRulesFixture.applyRulesForRequestPolicy(itemType, loanType,
        patronGroup, location);
  }

  private Policy applyNoticePolicy(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ItemLocation location) {

    return circulationRulesFixture.applyRulesForNoticePolicy(itemType, loanType,
      patronGroup, location);
  }

  private void matchesLoanPolicy(JsonArray array, int match, Policy policy,
      int line) {

    JsonObject o = array.getJsonObject(match);

    assertThat("Loan policy ID should match",
      o.getString("loanPolicyId"), is(policy.id));

    assertThat("Circulation rule line number should match",
      o.getInteger("circulationRuleLine"), is(line));
  }

  private void matchesRequestPolicy(JsonArray array, int match, Policy policy,
      int line) {

    JsonObject o = array.getJsonObject(match);

    assertThat("Request policy ID should match",
        o.getString("requestPolicyId"), is(policy.id));

    assertThat("Circulation rule line number should match",
        o.getInteger("circulationRuleLine"), is(line));
  }

  private void matchesNoticePolicy(JsonArray array, int match, Policy policy,
      int line) {

    JsonObject o = array.getJsonObject(match);

    assertThat("Notice policy ID should match",
        o.getString("noticePolicyId"), is(policy.id));

    assertThat("Circulation rule line number should match",
        o.getInteger("circulationRuleLine"), is(line));
  }

  private void applyRulesWithMissingParameters(String type, String itemType,
    String loanType, String patronGroup, String location,
    String missingParameterName) {

    final Response response = circulationRulesFixture
      .attemptToApplyRulesWithInvalidParameters(type, itemType, loanType,
        patronGroup, location);

    assertThat(response.getBody(), containsString(missingParameterName));
  }
}
