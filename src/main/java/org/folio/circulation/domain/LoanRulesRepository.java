package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoanRulesRepository {
  private final LoanRulesClient loanRulesClient;

  public LoanRulesRepository(Clients clients) {
    loanRulesClient = clients.loanRules();
  }

  private static CompletableFuture<HttpResult<String>> lookupLoanPolicyId(
    JsonObject item,
    JsonObject holding,
    JsonObject user,
    LoanRulesClient loanRulesClient) {

    CompletableFuture<HttpResult<String>> findLoanPolicyCompleted
      = new CompletableFuture<>();

    lookupLoanPolicyId(item, holding, user, loanRulesClient,
      findLoanPolicyCompleted::complete);

    return findLoanPolicyCompleted;
  }

  private static void lookupLoanPolicyId(
    JsonObject item,
    JsonObject holding,
    JsonObject user,
    LoanRulesClient loanRulesClient,
    Consumer<HttpResult<String>> onFinished) {

    if(item == null) {
      onFinished.accept(HttpResult.failure(
        new ServerErrorFailure("Unable to process claim for unknown item")));
      return;
    }

    if(holding == null) {
      onFinished.accept(HttpResult.failure(
        new ServerErrorFailure("Unable to process claim for unknown holding")));
      return;
    }

    String loanTypeId = determineLoanTypeForItem(item);
    String locationId = LoanValidation.determineLocationIdForItem(item, holding);

    //Got instance record, we're good to continue
    String materialTypeId = item.getString("materialTypeId");

    String patronGroup = user.getString("patronGroup");
    loanRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroup, response -> response.bodyHandler(body -> {
        Response getPolicyResponse = Response.from(response, body);

        if (getPolicyResponse.getStatusCode() == 404) {
          onFinished.accept(HttpResult.failure(
            new ServerErrorFailure("Unable to locate loan policy")));
        } else if (getPolicyResponse.getStatusCode() != 200) {
          onFinished.accept(HttpResult.failure(
            new ForwardOnFailure(getPolicyResponse)));
        } else {
          String policyId = getPolicyResponse.getJson().getString("loanPolicyId");
          onFinished.accept(HttpResult.success(policyId));
        }
      }));
  }

  private static String determineLoanTypeForItem(JsonObject item) {
    return item.containsKey("temporaryLoanTypeId") && !item.getString("temporaryLoanTypeId").isEmpty()
      ? item.getString("temporaryLoanTypeId")
      : item.getString("permanentLoanTypeId");
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupLoanPolicyId(
    LoanAndRelatedRecords relatedRecords) {

    return lookupLoanPolicyId(
      relatedRecords.inventoryRecords.getItem(),
      relatedRecords.inventoryRecords.holding,
      relatedRecords.requestingUser,
      loanRulesClient)
      .thenApply(result -> result.map(relatedRecords::withLoanPolicy));
  }
}
