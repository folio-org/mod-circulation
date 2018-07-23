package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;

public class UpdateLoanActionHistory {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient loansStorageClient;

  public UpdateLoanActionHistory(Clients clients) {
    loansStorageClient = clients.loansStorage();
  }

  private static <T> CompletableFuture<HttpResult<T>> skip(T previousResult) {
    return completedFuture(succeeded(previousResult));
  }

  //Updates the single open loan for the item related to a request
  CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestType requestType = RequestType.from(requestAndRelatedRecords.getRequest());

    String action = requestType.toLoanAction();
    String itemStatus = requestAndRelatedRecords.getRequest().getItem().getStatus();

    //Do not change any loans if no new status
    if(StringUtils.isEmpty(action)) {
      return skip(requestAndRelatedRecords);
    }

    String itemId = requestAndRelatedRecords.getRequest().getItemId();

    String queryTemplate = "query=itemId=%s+and+status.name=Open";
    String query = String.format(queryTemplate, itemId);

    return this.loansStorageClient.getMany(query).thenComposeAsync(
      getLoansResponse -> updateLatestLoan(requestAndRelatedRecords, action,
        itemStatus, itemId, getLoansResponse));
  }

  private CompletableFuture<HttpResult<RequestAndRelatedRecords>> updateLatestLoan(
    RequestAndRelatedRecords requestAndRelatedRecords,
    String action,
    String itemStatus,
    String itemId,
    Response getLoansResponse) {

    if(getLoansResponse.getStatusCode() == 200) {
      List<JsonObject> loans = JsonArrayHelper.toList(
        getLoansResponse.getJson().getJsonArray("loans"));

      if(loans.isEmpty()) {
        log.warn("No open loans found for item {}", itemId);
        //Only success in the sense that it can't be done, but no
        //compensating action to take
        return completedFuture(succeeded(requestAndRelatedRecords));
      }
      else if(loans.size() == 1) {
        JsonObject changedLoan = loans.get(0).copy();

        changedLoan.put("action", action);
        changedLoan.put("itemStatus", itemStatus);

        return this.loansStorageClient.put(changedLoan.getString("id"), changedLoan)
          .thenApply(putLoanResponse -> HttpResult.succeeded(requestAndRelatedRecords));
      }
      else {
        String moreThanOneOpenLoanError = String.format(
          "Received unexpected number (%s) of open loans for item %s",
          loans.size(), itemId);

        log.error(moreThanOneOpenLoanError);
        return completedFuture(failed(
          new ServerErrorFailure(moreThanOneOpenLoanError)));
      }
    } else {
      String failedError = String.format("Could not get open loans for item %s", itemId);

      log.error(failedError);
      return completedFuture(failed(new ServerErrorFailure(failedError)));
    }
  }
}
