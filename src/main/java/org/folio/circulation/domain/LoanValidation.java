package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;

public class LoanValidation {
  private static final String ITEM_BARCODE_PROPERTY_NAME = "itemBarcode";
  private static final String USER_BARCODE_PROPERTY_NAME = "userBarcode";
  private static final String PROXY_USER_BARCODE_PROPERTY_NAME = "proxyUserBarcode";

  private LoanValidation() { }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.getInventoryRecords().getItem() == null) {
        final String itemId = loan.getLoan().getItemId();

        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", ITEM_ID, itemId));
      }
      else {
        return result;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemBarcodeDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result, String barcode) {

    return result.next(loanAndRelatedRecords -> {
      if(loanAndRelatedRecords.getInventoryRecords().getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          String.format("No item with barcode %s exists", barcode),
          ITEM_BARCODE_PROPERTY_NAME, barcode));
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

    return loanAndRelatedRecords
      .next(loan -> {
      final RequestQueue requestQueue = loan.getRequestQueue();
      final JsonObject requestingUser = loan.getRequestingUser();

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          "userId", loan.getLoan().getUserId()));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenUserIsNotAwaitingPickup(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication between this and above
    return loanAndRelatedRecords.next(loan -> {
      final RequestQueue requestQueue = loan.getRequestQueue();
      final JsonObject requestingUser = loan.getRequestingUser();

      if(hasAwaitingPickupRequestForOtherPatron(requestQueue, requestingUser)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "User checking out must be requester awaiting pickup",
          USER_BARCODE_PROPERTY_NAME, barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenRequestingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    return loanAndRelatedRecords.next(loan -> {
      try {
        final User requestingUser = loan.getRequestingUser();

        if (!requestingUser.containsKey("active")) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot determine if user is active or not",
            USER_BARCODE_PROPERTY_NAME, barcode));
        }
        if (requestingUser.isInactive()) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot check out to inactive user",
            USER_BARCODE_PROPERTY_NAME, barcode));
        } else {
          return HttpResult.success(loan);
        }
      } catch (Exception e) {
        return HttpResult.failure(new ServerErrorFailure(e));
      }
    });
  }
  public static HttpResult<LoanAndRelatedRecords> refuseWhenProxyingUserIsInactive(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    return loanAndRelatedRecords.next(loan -> {
      final User proxyingUser = loan.getProxyingUser();

      if(proxyingUser == null) {
        return loanAndRelatedRecords;
      }
      else if (!proxyingUser.containsKey("active")) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Cannot determine if proxying user is active or not",
          PROXY_USER_BARCODE_PROPERTY_NAME, barcode));
      }
      else if(proxyingUser.isInactive()) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Cannot check out via inactive proxying user",
          PROXY_USER_BARCODE_PROPERTY_NAME, barcode));
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
      final Request highestPriority = requestQueue.getHighestPriorityFulfillableRequest();

      return isAwaitingPickup(highestPriority)
        && !isFor(highestPriority, requestingUser);
    }
  }

  private static boolean isFor(Request request, JsonObject user) {
    return StringUtils.equals(request.getUserId(), user.getString("id"));
  }

  private static boolean isAwaitingPickup(Request highestPriority) {
    return StringUtils.equals(highestPriority.getStatus(), OPEN_AWAITING_PICKUP);
  }

  public static CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenHasOpenLoan(
    LoanAndRelatedRecords loanAndRelatedRecords,
    LoanRepository loanRepository,
    String barcode) {

    final String itemId = loanAndRelatedRecords.getLoan().getItemId();

    return loanRepository.hasOpenLoan(itemId)
      .thenApply(r -> r.next(openLoan -> {
        if(openLoan) {
          return HttpResult.failure(new ValidationErrorFailure(
            "Cannot check out item that already has an open loan", ITEM_BARCODE_PROPERTY_NAME, barcode));
        }
        else {
          return HttpResult.success(loanAndRelatedRecords);
        }
      }));
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords) {

    return loanAndRelatedRecords.next(loan -> {
      final JsonObject item = loan.getInventoryRecords().getItem();

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", "itemId", item.getString("id")));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemIsAlreadyCheckedOut(
    HttpResult<LoanAndRelatedRecords> loanAndRelatedRecords, String barcode) {

    //TODO: Extract duplication with above
    return loanAndRelatedRecords.next(loan -> {
      final JsonObject item = loan.getInventoryRecords().getItem();

      if(ItemStatus.isCheckedOut(item)) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item is already checked out", ITEM_BARCODE_PROPERTY_NAME, barcode));
      }
      else {
        return loanAndRelatedRecords;
      }
    });
  }
}
