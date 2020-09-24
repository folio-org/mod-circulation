package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.UpdatedRequestPair;

import java.util.List;
import java.util.stream.Collectors;

public class CirculationCheckInCheckOutLogEventMapper {

  public static final String CHECK_IN = "CHECK_IN_EVENT";
  public static final String CHECK_OUT = "CHECK_OUT_EVENT";

  public static final String PL_LOG_EVENT_TYPE = "logEventType";
  public static final String CLAIMED_RETURNED_RESOLUTION = "claimedReturnedResolution";
  public static final String PL_SERVICE_POINT_ID = "servicePointId";
  public static final String PL_REQUESTS = "requests";
  public static final String PL_ITEM_ID = "itemId";
  public static final String PL_ITEM_BARCODE = "itemBarcode";
  public static final String PL_ITEM_STATUS_NAME = "itemStatusName";
  public static final String PL_IS_CLAIMED_RETURNED = "isClaimedReturned";
  public static final String PL_DESTINATION_SERVICE_POINT = "destinationServicePoint";
  public static final String ITEM_SOURCE = "source";
  public static final String PL_SOURCE = "source";
  public static final String PL_LOAN_ID = "loanId";
  public static final String PL_IS_LOAN_CLOSED = "isLoanClosed";
  public static final String PL_SYSTEM_RETURN_DATE = "systemReturnDate";
  public static final String PL_RETURN_DATE = "returnDate";
  public static final String PL_DUE_DATE = "dueDate";
  public static final String PL_USER_ID = "userId";
  public static final String PL_USER_BARCODE = "userBarcode";
  public static final String PL_PROXY_BARCODE = "proxyBarcode";
  public static final String PL_ID = "id";
  public static final String PL_OLD_REQUEST_STATUS = "oldRequestStatus";
  public static final String PL_NEW_REQUEST_STATUS = "newRequestStatus";
  public static final String PL_PICK_UP_SERVICE_POINT = "pickUpServicePoint";
  public static final String PL_REQUEST_TYPE = "requestType";
  public static final String PL_ADDRESS_TYPE = "addressType";

  public static JsonObject mapToCheckInLogEventJson(CheckInContext checkInContext) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, PL_LOG_EVENT_TYPE, CHECK_IN);
    write(logEventPayload, PL_SERVICE_POINT_ID, checkInContext.getCheckInServicePointId());

    populateLoanData(checkInContext, logEventPayload);
    populateItemData(checkInContext, logEventPayload);

    ofNullable(checkInContext.getCheckInRequest())
      .flatMap(checkInRequest -> ofNullable(checkInRequest.getClaimedReturnedResolution()))
      .ifPresent(resolution -> write(logEventPayload, CLAIMED_RETURNED_RESOLUTION, resolution.getValue()));

    write(logEventPayload, PL_REQUESTS, getUpdatedRequests(checkInContext));

    return logEventPayload;
  }

  public static JsonObject mapToCheckOutLogEventJson(LoanAndRelatedRecords loanAndRelatedRecords) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, PL_LOG_EVENT_TYPE, CHECK_OUT);
    write(logEventPayload, PL_SERVICE_POINT_ID, loanAndRelatedRecords.getLoan().getCheckoutServicePointId());

    populateLoanData(loanAndRelatedRecords, logEventPayload);
    populateItemData(loanAndRelatedRecords, logEventPayload);

    write(logEventPayload, PL_REQUESTS, getUpdatedRequests(loanAndRelatedRecords));

    return logEventPayload;
  }

  private static void populateItemData(CheckInContext checkInContext, JsonObject logEventPayload) {
    ofNullable(checkInContext.getItem())
      .ifPresent(item -> {
        write(logEventPayload, PL_ITEM_ID, item.getItemId());
        write(logEventPayload, PL_ITEM_BARCODE, item.getBarcode());
        write(logEventPayload, PL_ITEM_STATUS_NAME, item.getStatusName());
        write(logEventPayload, PL_IS_CLAIMED_RETURNED, item.isClaimedReturned());
        ofNullable(item.getInTransitDestinationServicePoint())
          .ifPresent(sp -> write(logEventPayload, PL_DESTINATION_SERVICE_POINT, sp.getName()));
        ofNullable(item.getMaterialType())
          .ifPresent(mt -> write(logEventPayload, PL_SOURCE, mt.getString(ITEM_SOURCE)));
      });
  }

  private static void populateItemData(LoanAndRelatedRecords loanAndRelatedRecords, JsonObject logEventPayload) {
    ofNullable(loanAndRelatedRecords.getLoan().getItem())
      .ifPresent(item -> {
        write(logEventPayload, PL_ITEM_ID, item.getItemId());
        write(logEventPayload, PL_ITEM_BARCODE, item.getBarcode());
        write(logEventPayload, PL_ITEM_STATUS_NAME, item.getStatusName());
        ofNullable(item.getMaterialType())
          .ifPresent(mt -> write(logEventPayload, PL_SOURCE, mt.getString(ITEM_SOURCE)));
      });
  }

  private static void populateLoanData(CheckInContext checkInContext, JsonObject logEventPayload) {
    populateLoanData(checkInContext.getLoan(), logEventPayload);
  }

  private static void populateLoanData(LoanAndRelatedRecords loanAndRelatedRecords, JsonObject logEventPayload) {
    populateLoanData(loanAndRelatedRecords.getLoan(), logEventPayload);
  }

  private static void populateLoanData(Loan checkInCheckOutLoan, JsonObject logEventPayload) {
    ofNullable(checkInCheckOutLoan)
      .ifPresent(loan -> {
        write(logEventPayload, PL_LOAN_ID, loan.getId());
        write(logEventPayload, PL_IS_LOAN_CLOSED, loan.isClosed());
        write(logEventPayload, PL_SYSTEM_RETURN_DATE, loan.getSystemReturnDate());
        write(logEventPayload, PL_RETURN_DATE, loan.getReturnDate());
        write(logEventPayload, PL_DUE_DATE, loan.getDueDate());
        ofNullable(loan.getUser())
          .ifPresent(user -> {
            write(logEventPayload, PL_USER_ID, user.getId());
            write(logEventPayload, PL_USER_BARCODE, user.getBarcode());
          });
        ofNullable(loan.getProxy())
          .ifPresent(proxy -> write(logEventPayload, PL_PROXY_BARCODE, proxy.getBarcode()));
      });
  }

  private static JsonArray getUpdatedRequests(CheckInContext checkInContext) {
    return mapUpdatedRequestPairsToJsonArray(checkInContext.getRequestQueue().getUpdatedRequests());
  }

  private static JsonArray getUpdatedRequests(LoanAndRelatedRecords loanAndRelatedRecords) {
    return mapUpdatedRequestPairsToJsonArray(loanAndRelatedRecords.getRequestQueue().getUpdatedRequests());
  }

  private static JsonArray mapUpdatedRequestPairsToJsonArray(List<UpdatedRequestPair> updatedRequestPairs) {
    return new JsonArray(updatedRequestPairs.stream()
      .map(request -> {
        JsonObject requestPayload = new JsonObject();
        write(requestPayload, PL_ID, request.getUpdated().getId());
        write(requestPayload, PL_OLD_REQUEST_STATUS, request.getOriginal().getStatus().getValue());
        write(requestPayload, PL_NEW_REQUEST_STATUS, request.getUpdated().getStatus().getValue());
        ofNullable(request.getUpdated().getPickupServicePoint()).ifPresent(sp -> write(requestPayload, PL_PICK_UP_SERVICE_POINT, sp.getName()));
        ofNullable(request.getUpdated().getRequestType()).ifPresent(rt -> write(requestPayload, PL_REQUEST_TYPE, rt.getValue()));
        ofNullable(request.getUpdated().getAddressType()).ifPresent(at -> write(requestPayload, PL_ADDRESS_TYPE, at.getName()));

        return requestPayload;
      }).collect(Collectors.toList())
    );
  }
}
