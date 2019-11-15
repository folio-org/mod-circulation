package api.support.fixtures;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;

import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class CirculationRulesFixture {
  private final OkapiHttpClient client;

  public CirculationRulesFixture(OkapiHttpClient client) {
    this.client = client;
  }

  public void updateCirculationRules(UUID loanPolicyId, UUID requestPolicyId,
                                     UUID noticePolicyId, UUID overdueFinePolicyId,
                                     UUID lostItemFeePolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    String rule = soleFallbackPolicyRule(loanPolicyId, requestPolicyId, noticePolicyId, overdueFinePolicyId,
      lostItemFeePolicyId);

    updateCirculationRules(rule);
  }

  public void updateCirculationRules(String rules)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject circulationRulesRequest = new JsonObject()
      .put("rulesAsText", rules);

    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(InterfaceUrls.circulationRulesUrl(), circulationRulesRequest,
      ResponseHandler.any(completed));

    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to set circulation rules: %s", response.getBody()),
      response.getStatusCode(), is(204));
  }

  private String soleFallbackPolicyRule(UUID loanPolicyId, UUID requestPolicyId,
                                        UUID noticePolicyId, UUID overdueFinePolicyId,
                                        UUID lostItemFeePolicyId) {

    return soleFallbackPolicyRule(loanPolicyId.toString(), requestPolicyId.toString(),
      noticePolicyId.toString(), overdueFinePolicyId.toString(), lostItemFeePolicyId.toString());
  }

  public String soleFallbackPolicyRule(String loanPolicyId, String requestPolicyId,
                                       String noticePolicyId, String overdueFinePolicyId,
                                       String lostItemFeePolicyId) {

    return String.format("priority: t, s, c, b, a, m, g%nfallback-policy: l %s r %s n %s o %s i %s%n",
      loanPolicyId, requestPolicyId, noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);
  }
}
