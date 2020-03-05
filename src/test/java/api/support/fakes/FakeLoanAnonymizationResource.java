package api.support.fakes;

import static api.support.APITestContext.createWebClient;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;

import api.support.http.InterfaceUrls;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class FakeLoanAnonymizationResource implements Handler<RoutingContext> {
  @Override
  public void handle(RoutingContext routingContext) {
    routingContext.request().bodyHandler(body -> {
      final JsonObject responseJson = new JsonObject();
      final JsonObject requestJson = body.toJsonObject();

      final List<String> loanIds = requestJson.getJsonArray("loanIds").stream()
        .map(Object::toString)
        .collect(Collectors.toList());

      anonymizeLoans(loanIds).thenAccept(notUsed -> {
        responseJson.put("anonymizedLoans", new JsonArray(loanIds));
        responseJson.put("notAnonymizedLoans", new JsonArray());

        routingContext.response()
          .putHeader("Content-type", "application/json")
          .setStatusCode(200)
          .end(responseJson.encode());
      });
    });
  }

  private CompletableFuture<Void> anonymizeLoans(List<String> loanIds) {
    if (loanIds.isEmpty()) {
      return completedFuture(null);
    }

    CompletableFuture<Response> future = completedFuture(null);
    for (String loanId : loanIds) {
      future = future.thenCompose(prev -> getLoanById(loanId))
        .thenCompose(loan -> {
          JsonObject json = loan.getJson().copy();

          json.remove("userId");
          json.remove("borrower");

          return updateLoan(json);
        });
    }

    return future.thenApply(notUsed -> null);
  }

  private CompletableFuture<Response> getLoanById(String id) {
    return createWebClient().get(InterfaceUrls.loansStorageUrl("/" + id))
      .thenApply(Result::value);
  }

  private CompletableFuture<Response> updateLoan(JsonObject loan) {
    final String id = loan.getString("id");

    return createWebClient().put(InterfaceUrls.loansStorageUrl("/" + id), loan)
      .thenApply(Result::value);
  }
}
