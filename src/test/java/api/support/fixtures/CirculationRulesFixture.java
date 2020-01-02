package api.support.fixtures;

import static api.support.RestAssuredResponseConversion.toResponse;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.Collection;
import java.util.UUID;

import org.folio.circulation.rules.ItemLocation;
import org.folio.circulation.rules.ItemType;
import org.folio.circulation.rules.LoanType;
import org.folio.circulation.rules.PatronGroup;
import org.folio.circulation.rules.Policy;
import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.http.QueryStringParameter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CirculationRulesFixture {
  private final RestAssuredClient restAssuredClient;

  public CirculationRulesFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response putRules(String body) {
    return toResponse(restAssuredClient
      .beginRequest("put-circulation-rules")
      .body(body)
      .when().put(circulationRulesUrl())
      .then().extract().response());
  }

  public void updateCirculationRules(UUID loanPolicyId, UUID requestPolicyId,
      UUID noticePolicyId, UUID overdueFinePolicyId, UUID lostItemFeePolicyId) {

    String rule = soleFallbackPolicyRule(loanPolicyId, requestPolicyId,
        noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);

    updateCirculationRules(rule);
  }

  public void updateCirculationRules(String rules) {
    JsonObject circulationRulesRequest = new JsonObject()
      .put("rulesAsText", rules);

    final Response response = putRules(circulationRulesRequest.encodePrettily());

    assertThat(String.format(
      "Failed to set circulation rules: %s", response.getBody()),
      response.getStatusCode(), is(204));
  }

  private String soleFallbackPolicyRule(UUID loanPolicyId, UUID requestPolicyId,
      UUID noticePolicyId, UUID overdueFinePolicyId, UUID lostItemFeePolicyId) {

    return soleFallbackPolicyRule(loanPolicyId.toString(),
        requestPolicyId.toString(), noticePolicyId.toString(),
        overdueFinePolicyId.toString(), lostItemFeePolicyId.toString());
  }

  public String soleFallbackPolicyRule(String loanPolicyId,
      String requestPolicyId, String noticePolicyId, String overdueFinePolicyId,
      String lostItemFeePolicyId) {

    return String.format("priority: t, s, c, b, a, m, g%nfallback-policy: l %s r %s n %s o %s i %s%n",
        loanPolicyId, requestPolicyId, noticePolicyId, overdueFinePolicyId,
        lostItemFeePolicyId);
  }

  public Response getRules() {
    return toResponse(restAssuredClient
      .beginRequest("get-circulation-rules")
      .when().get(circulationRulesUrl())
      .then().extract().response());
  }

  public Policy applyRulesForLoanPolicy(ItemType itemType, LoanType loanType,
    PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
        patronGroup, location, "/loan-policy", "apply-rules-to-get-loan-policy");

    String loanPolicyId = response.getJson().getString("loanPolicyId");

    assertThat(loanPolicyId, is(not(nullValue())));

    return new Policy(loanPolicyId);
  }

  public JsonArray applyAllRulesForLoanPolicy(ItemType itemType,
      LoanType loanType, PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
        patronGroup, location, "/loan-policy-all",
        "apply-all-rules-to-get-loan-policy");

    return response.getJson().getJsonArray("circulationRuleMatches");
  }

  public Policy applyRulesForRequestPolicy(ItemType itemType, LoanType loanType,
    PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
        patronGroup, location, "/request-policy",
        "apply-rules-to-get-request-policy");

    String requestPolicyId = response.getJson().getString("requestPolicyId");

    assertThat(requestPolicyId, is(not(nullValue())));

    return new Policy(requestPolicyId);
  }

  public JsonArray applyAllRulesForRequestPolicy(ItemType itemType,
    LoanType loanType, PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
        patronGroup, location, "/request-policy-all",
        "apply-all-rules-to-get-request-policy");

    return response.getJson().getJsonArray("circulationRuleMatches");
  }

  public Policy applyRulesForNoticePolicy(ItemType itemType, LoanType loanType,
    PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
        patronGroup, location, "/notice-policy",
        "apply-rules-to-get-notice-policy");

    String requestPolicyId = response.getJson().getString("noticePolicyId");

    assertThat(requestPolicyId, is(not(nullValue())));

    return new Policy(requestPolicyId);
  }

  public JsonArray applyAllRulesForNoticePolicy(ItemType itemType,
    LoanType loanType, PatronGroup patronGroup, ItemLocation location) {

    final Response response = applyRulesForPolicy(itemType, loanType,
      patronGroup, location, "/notice-policy-all",
      "apply-all-rules-to-get-notice-policy");

    return response.getJson().getJsonArray("circulationRuleMatches");
  }

  private Response applyRulesForPolicy(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ItemLocation location, String policyPath,
      String requestId) {

    return restAssuredClient.get(
      circulationRulesUrl(policyPath),
      getApplyParameters(itemType, loanType, patronGroup, location), 200,
      requestId);
  }

  private Collection<QueryStringParameter> getApplyParameters(ItemType itemType,
      LoanType loanType, PatronGroup patronGroup, ItemLocation location) {

    return asList(
      namedParameter("item_type_id", itemType.id),
      namedParameter("loan_type_id", loanType.id),
      namedParameter("patron_type_id", patronGroup.id),
      namedParameter("location_id", location.id));
  }
}
