package api.support.fixtures;

import static api.support.RestAssuredResponseConversion.toResponse;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
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
}
