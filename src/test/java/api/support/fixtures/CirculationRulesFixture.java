package api.support.fixtures;

import static api.support.RestAssuredResponseConversion.toResponse;
import static api.support.http.InterfaceUrls.circulationRulesStorageUrl;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.folio.circulation.rules.ItemLocation;
import org.folio.circulation.rules.ItemType;
import org.folio.circulation.rules.LoanType;
import org.folio.circulation.rules.PatronGroup;
import org.folio.circulation.rules.Policy;
import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.http.QueryStringParameter;
import io.netty.util.internal.StringUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CirculationRulesFixture {
  private final RestAssuredClient restAssuredClient;
  private final LoanPoliciesFixture loanPoliciesFixture;
  private final NoticePoliciesFixture noticePoliciesFixture;
  private final RequestPoliciesFixture requestPoliciesFixture;
  private final OverdueFinePoliciesFixture overdueFinePoliciesFixture;
  private final LostItemFeePoliciesFixture lostItemFeePoliciesFixture;

  public CirculationRulesFixture(RestAssuredClient restAssuredClient,
    LoanPoliciesFixture loanPoliciesFixture, NoticePoliciesFixture noticePoliciesFixture,
    RequestPoliciesFixture requestPoliciesFixture, OverdueFinePoliciesFixture overdueFinePoliciesFixture,
    LostItemFeePoliciesFixture lostItemFeePoliciesFixture) {

    this.restAssuredClient = restAssuredClient;
    this.loanPoliciesFixture = loanPoliciesFixture;
    this.noticePoliciesFixture = noticePoliciesFixture;
    this.requestPoliciesFixture = requestPoliciesFixture;
    this.overdueFinePoliciesFixture = overdueFinePoliciesFixture;
    this.lostItemFeePoliciesFixture = lostItemFeePoliciesFixture;
  }

  public String getCirculationRules() {
    Response getResponse = restAssuredClient.get(circulationRulesUrl(),
      "get-circulation-rules");

    JsonObject rulesJson = new JsonObject(getResponse.getBody());

    return rulesJson.getString("rulesAsText");
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

    loanPoliciesFixture.create(getPolicyFromRule(rules, "l"));
    noticePoliciesFixture.create(getPolicyFromRule(rules, "n"));
    requestPoliciesFixture.allowAllRequestPolicy(getPolicyFromRule(rules, "r"));
    overdueFinePoliciesFixture.create(getPolicyFromRule(rules, "o"));
    lostItemFeePoliciesFixture.create(getPolicyFromRule(rules, "i"));

    final Response response = putRules(circulationRulesRequest.encodePrettily());
    assertThat(String.format(
      "Failed to set circulation rules: %s", response.getBody()),
      response.getStatusCode(), is(204));
  }

  private List<String> getPolicyFromRule(String rules, String policyType) {
    List<String> allMatches = new ArrayList<>();
    String regex = String.format(
      "(?<=\\b%s\\s)[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-5][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}", policyType);
    Matcher m = Pattern.compile(regex)
      .matcher(rules);
    while (m.find()) {
      allMatches.add(m.group());
    }
    return allMatches;
  }

  public void updateCirculationRulesWithoutInvalidatingCache(String rules) {
    JsonObject json = new JsonObject().put("rulesAsText", rules);

    restAssuredClient.beginRequest("update-rules-in-storage")
      .body(json.encodePrettily())
      .when()
      .put(circulationRulesStorageUrl(""))
      .then()
      .log().all()
      .statusCode(204);
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

  public Response attemptToApplyRulesWithNoParameters(String path) {
    return restAssuredClient.get(circulationRulesUrl(path), 400,
      "apply-rules-with-no-parameters");
  }

  public Response attemptToApplyRulesWithInvalidParameters(String type,
    String itemType, String loanType, String patronGroup, String location) {

    final List<QueryStringParameter> parameters = new ArrayList<>();

    if(itemType != null) {
      parameters.add(namedParameter("item_type_id", itemType));
    }

    if(loanType != null) {
      parameters.add(namedParameter("loan_type_id", loanType));
    }

    if(patronGroup != null) {
      parameters.add(namedParameter("patron_type_id", patronGroup));
    }

    if(location != null) {
      parameters.add(namedParameter("location_id", location));
    }

    return restAssuredClient.get(
      circulationRulesUrl("/" + type + "-policy"), parameters,
      400, "apply-rules-with-invalid-parameters");
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
