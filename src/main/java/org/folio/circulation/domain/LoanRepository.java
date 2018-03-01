package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

public class LoanRepository {
  private final CollectionResourceClient loansStorageClient;

  public LoanRepository(Clients clients) {
    loansStorageClient = clients.loansStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords relatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject loan = relatedRecords.loan;

    loan.put("loanPolicyId", relatedRecords.loanPolicyId);
    loan.put("itemStatus", ItemStatus.getStatus(relatedRecords.inventoryRecords.item));

    loansStorageClient.post(loan, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          relatedRecords.withLoan(response.getJson())));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }
}
