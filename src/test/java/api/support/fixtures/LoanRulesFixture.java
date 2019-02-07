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

public class LoanRulesFixture {
  private final OkapiHttpClient client;

  public LoanRulesFixture(OkapiHttpClient client) {
    this.client = client;
  }

  public void updateLoanRules(UUID loanPolicyId, UUID requestPolicyId, UUID noticePolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    String rule = soleFallbackPolicyRule(loanPolicyId, requestPolicyId, noticePolicyId);

    updateLoanRules(rule);
  }

  public void updateLoanRules(String rules)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject loanRulesRequest = new JsonObject()
      .put("loanRulesAsTextFile", rules);

    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(InterfaceUrls.loanRulesUrl(), loanRulesRequest,
      ResponseHandler.any(completed));

    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to set loan rules: %s", response.getBody()),
      response.getStatusCode(), is(204));
  }

  private String soleFallbackPolicyRule(UUID loanPolicyId, UUID requestPolicyId, UUID noticePolicyId) {
    return String.format("priority: t, s, c, b, a, m, g%nfallback-policy: l %s r %s n %s%n",
      loanPolicyId, requestPolicyId, noticePolicyId);
  }
}
