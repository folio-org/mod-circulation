package org.folio.circulation.domain;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.Consumer;

public class LoanActionHistoryAssistant {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //Updates the single open loan for the item related to a request
  public static void  updateLoanActionHistory(
    RequestAndRelatedRecords requestAndRelatedRecords,
    String newAction,
    String newItemStatus,
    CollectionResourceClient loansStorageClient,
    HttpServerResponse responseToClient,
    Consumer<Void> onSuccess) {

    //Do not change any loans if no new status
    if(StringUtils.isEmpty(newAction)) {
      onSuccess.accept(null);
      return;
    }

    String itemId = requestAndRelatedRecords.request.getString("itemId");

    String queryTemplate = "query=itemId=%s+and+status.name=Open";
    String query = String.format(queryTemplate, itemId);

    loansStorageClient.getMany(query, getLoansResponse -> {
      if(getLoansResponse.getStatusCode() == 200) {
        List<JsonObject> loans = JsonArrayHelper.toList(
          getLoansResponse.getJson().getJsonArray("loans"));

        if(loans.size() == 0) {
          log.info(String.format("No open loans found for item", itemId));
          onSuccess.accept(null);
        }
        else if(loans.size() == 1) {
          JsonObject changedLoan = loans.get(0).copy();

          changedLoan.put("action", newAction);
          changedLoan.put("itemStatus", newItemStatus);

          loansStorageClient.put(changedLoan.getString("id"), changedLoan,
            putLoanResponse -> {
              onSuccess.accept(null);
          });
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
  }
}
