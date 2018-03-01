package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;

import java.util.concurrent.CompletableFuture;

public class LoanRepository {
  private final CollectionResourceClient loansStorageClient;

  public LoanRepository(Clients clients) {
    loansStorageClient = clients.loansStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject storageLoan = convertLoanToStorageRepresentation(
      loanAndRelatedRecords.loan, loanAndRelatedRecords.inventoryRecords.item);

    storageLoan.put("loanPolicyId", loanAndRelatedRecords.loanPolicyId);

    loansStorageClient.post(storageLoan, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          loanAndRelatedRecords.withLoan(response.getJson())));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onUpdated = new CompletableFuture<>();

    JsonObject storageLoan = convertLoanToStorageRepresentation(
      loanAndRelatedRecords.loan, loanAndRelatedRecords.inventoryRecords.item);

    loansStorageClient.put(storageLoan.getString("id"), storageLoan, response -> {
      if (response.getStatusCode() == 204) {
        onUpdated.complete(HttpResult.success(loanAndRelatedRecords));
      } else {
        onUpdated.complete(HttpResult.failure(new ServerErrorFailure("Failed to update loan")));
      }
    });

    return onUpdated;
  }

  private static JsonObject convertLoanToStorageRepresentation(
    JsonObject loan,
    JsonObject item) {

    JsonObject storageLoan = loan.copy();

    storageLoan.remove("item");
    storageLoan.remove("itemStatus");
    storageLoan.put("itemStatus", ItemStatus.getStatus(item));

    return storageLoan;
  }
}
