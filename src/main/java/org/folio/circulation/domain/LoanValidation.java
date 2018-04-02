package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;

public class LoanValidation {
  private LoanValidation() { }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getItem() == null) {
        final String itemId = loan.loan.getString("itemId");

        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId", itemId));
      }
      else {
        return result;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemBarcodeDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result, String barcode) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          String.format("No item with barcode %s exists", barcode),
          "itemBarcode", barcode));
      }
      else {
        return result;
      }
    });
  }

  public static void defaultStatusAndAction(JsonObject loan) {
    if(!loan.containsKey("status")) {
      loan.put("status", new JsonObject().put("name", "Open"));

      if(!loan.containsKey("action")) {
        loan.put("action", "checkedout");
      }
    }
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final RequestQueue requestQueue = loan.requestQueue;
      final JsonObject requestingUser = loan.requestingUser;

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          "userId", loan.loan.getString("userId")));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  private static boolean hasAwaitingPickupRequestForOtherPatron(
    RequestQueue requestQueue,
    JsonObject requestingUser) {

    if(!requestQueue.hasOutstandingFulfillableRequests()) {
      return false;
    }
    else {
      final JsonObject highestPriority = requestQueue.getHighestPriorityFulfillableRequest();

      return isAwaitingPickup(highestPriority)
        && !isFor(highestPriority, requestingUser);
    }
  }

  private static boolean isFor(JsonObject request, JsonObject user) {
    return StringUtils.equals(request.getString("requesterId"), user.getString("id"));
  }

  private static boolean isAwaitingPickup(JsonObject highestPriority) {
    return StringUtils.equals(highestPriority.getString("status"), OPEN_AWAITING_PICKUP);
  }

  public static String determineLocationIdForItem(JsonObject item, JsonObject holding) {
    if(item != null && item.containsKey("temporaryLocationId")) {
      return item.getString("temporaryLocationId");
    }
    else if(holding != null && holding.containsKey("permanentLocationId")) {
      return holding.getString("permanentLocationId");
    }
    else if(item != null && item.containsKey("permanentLocationId")) {
      return item.getString("permanentLocationId");
    }
    else {
      return null;
    }
  }
}
