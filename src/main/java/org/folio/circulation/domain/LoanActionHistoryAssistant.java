package org.folio.circulation.domain;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LoanActionHistoryAssistant {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //Updates the single open loan for the item related to a request
  public static CompletableFuture<HttpResult<RequestAndRelatedRecords>> updateLoanActionHistory(
    RequestAndRelatedRecords requestAndRelatedRecords,
    CollectionResourceClient loansStorageClient,
    HttpServerResponse responseToClient) {

    RequestType requestType = RequestType.from(requestAndRelatedRecords.request);

    String action = requestType.toLoanAction();
    String itemStatus = ItemStatus.getStatus(
      requestAndRelatedRecords.inventoryRecords.item);

    //Do not change any loans if no new status
    if(StringUtils.isEmpty(action)) {
      return skip(requestAndRelatedRecords);
    }

    String itemId = requestAndRelatedRecords.request.getString("itemId");

    String queryTemplate = "query=itemId=%s+and+status.name=Open";
    String query = String.format(queryTemplate, itemId);

    CompletableFuture<HttpResult<RequestAndRelatedRecords>> completed = new CompletableFuture<>();

    loansStorageClient.getMany(query, getLoansResponse -> {
      if(getLoansResponse.getStatusCode() == 200) {
        List<JsonObject> loans = JsonArrayHelper.toList(
          getLoansResponse.getJson().getJsonArray("loans"));

        if(loans.isEmpty()) {
          log.warn("No open loans found for item {}", itemId);
          //Only success in the sense that it can't be done, but no
          //compensating action to take
          completed.complete(HttpResult.success(requestAndRelatedRecords));
        }
        else if(loans.size() == 1) {
          JsonObject changedLoan = loans.get(0).copy();

          changedLoan.put("action", action);
          changedLoan.put("itemStatus", itemStatus);

          loansStorageClient.put(changedLoan.getString("id"), changedLoan,
            putLoanResponse ->
              completed.complete(HttpResult.success(requestAndRelatedRecords)));
        }
        else {
          String moreThanOneOpenLoanError = String.format(
            "Received unexpected number (%s) of open loans for item %s",
            loans.size(), itemId);

          log.error(moreThanOneOpenLoanError);
          ServerErrorResponse.internalError(responseToClient,
            moreThanOneOpenLoanError);
        }
      } else {
        String failedError = String.format("Could not get open loans for item %s", itemId);

        log.error(failedError);
        ServerErrorResponse.internalError(responseToClient,
          failedError);
      }
    });

    return completed;
  }

  private static <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return CompletableFuture.completedFuture(HttpResult.success(previousResult));
  }
}
