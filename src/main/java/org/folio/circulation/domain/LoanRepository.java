package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getById(String id) {
    CompletableFuture<Response> getLoanCompleted = new CompletableFuture<>();

    loansStorageClient.get(id, getLoanCompleted::complete);

    final Function<Response, HttpResult<LoanAndRelatedRecords>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(new LoanAndRelatedRecords(response.getJson()));
      }
      else {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
    };

    return getLoanCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
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
