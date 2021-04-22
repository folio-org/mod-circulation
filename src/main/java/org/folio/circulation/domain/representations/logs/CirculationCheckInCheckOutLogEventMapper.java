package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.*;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_OUT;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.UpdatedRequestPair;
import org.folio.circulation.domain.User;

import java.util.List;
import java.util.stream.Collectors;

public class CirculationCheckInCheckOutLogEventMapper {

  private CirculationCheckInCheckOutLogEventMapper() {
  }

  /**
   * This method returns {@link String} content for check-in log event payload
   * @param checkInContext check-in flow context {@link CheckInContext}
   * @return check-in log event payload
   */
  public static String mapToCheckInLogEventContent(CheckInContext checkInContext, User loggedInUser) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), CHECK_IN.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), checkInContext.getCheckInServicePointId());

    populateLoanData(checkInContext, logEventPayload);
    populateItemData(checkInContext, logEventPayload, loggedInUser);

    ofNullable(checkInContext.getCheckInRequest())
      .flatMap(checkInRequest -> ofNullable(checkInRequest.getClaimedReturnedResolution()))
      .ifPresent(resolution -> write(logEventPayload, CLAIMED_RETURNED_RESOLUTION.value(), resolution.getValue()));

    write(logEventPayload, REQUESTS.value(), getUpdatedRequests(checkInContext));

    return logEventPayload.encode();
  }

  /**
   * This method returns {@link String} content for check-out log event payload
   * @param loanAndRelatedRecords check-out flow context {@link LoanAndRelatedRecords}
   * @return check-out log event payload
   */
  public static String mapToCheckOutLogEventContent(LoanAndRelatedRecords loanAndRelatedRecords, User loggedInUser) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), CHECK_OUT.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), loanAndRelatedRecords.getLoan().getCheckoutServicePointId());

    populateLoanData(loanAndRelatedRecords, logEventPayload);
    populateItemData(loanAndRelatedRecords, logEventPayload, loggedInUser);

    write(logEventPayload, REQUESTS.value(), getUpdatedRequests(loanAndRelatedRecords));

    return logEventPayload.encode();
  }

  private static void populateItemData(CheckInContext checkInContext, JsonObject logEventPayload, User loggedInUser) {
    ofNullable(checkInContext.getItem())
      .ifPresent(item -> {
        write(logEventPayload, ITEM_ID.value(), item.getItemId());
        write(logEventPayload, ITEM_BARCODE.value(), item.getBarcode());
        write(logEventPayload, ITEM_STATUS_NAME.value(), item.getStatus().getValue());
        write(logEventPayload, HOLDINGS_RECORD_ID.value(), item.getHoldingsRecordId());
        write(logEventPayload, INSTANCE_ID.value(), item.getInstanceId());
        ofNullable(item.getInTransitDestinationServicePoint())
          .ifPresent(sp -> write(logEventPayload, DESTINATION_SERVICE_POINT.value(), sp.getName()));
      });
    write(logEventPayload, SOURCE.value(), loggedInUser.getPersonalName());
  }

  private static void populateItemData(LoanAndRelatedRecords loanAndRelatedRecords, JsonObject logEventPayload, User loggedInUser) {
    ofNullable(loanAndRelatedRecords.getLoan().getItem())
      .ifPresent(item -> {
        write(logEventPayload, ITEM_ID.value(), item.getItemId());
        write(logEventPayload, ITEM_BARCODE.value(), item.getBarcode());
        write(logEventPayload, ITEM_STATUS_NAME.value(), item.getStatusName());
        write(logEventPayload, HOLDINGS_RECORD_ID.value(), item.getHoldingsRecordId());
        write(logEventPayload, INSTANCE_ID.value(), item.getInstanceId());
      });
    write(logEventPayload, SOURCE.value(), loggedInUser.getPersonalName());
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
        write(logEventPayload, LOAN_ID.value(), loan.getId());
        write(logEventPayload, IS_LOAN_CLOSED.value(), loan.isClosed());
        write(logEventPayload, SYSTEM_RETURN_DATE.value(), loan.getSystemReturnDate());
        write(logEventPayload, RETURN_DATE.value(), loan.getReturnDate());
        write(logEventPayload, DUE_DATE.value(), loan.getDueDate());
        ofNullable(loan.getUser())
          .ifPresent(user -> {
            write(logEventPayload, USER_ID.value(), user.getId());
            write(logEventPayload, USER_BARCODE.value(), user.getBarcode());
          });
        ofNullable(loan.getProxy())
          .ifPresent(proxy -> write(logEventPayload, PROXY_BARCODE.value(), proxy.getBarcode()));
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
        write(requestPayload, REQUEST_ID.value(), request.getUpdated().getId());
        write(requestPayload, OLD_REQUEST_STATUS.value(), request.getOriginal().getStatus().getValue());
        write(requestPayload, NEW_REQUEST_STATUS.value(), request.getUpdated().getStatus().getValue());
        ofNullable(request.getUpdated().getPickupServicePoint()).ifPresent(sp -> write(requestPayload, PICK_UP_SERVICE_POINT.value(), sp.getName()));
        ofNullable(request.getUpdated().getRequestType()).ifPresent(rt -> write(requestPayload, REQUEST_TYPE.value(), rt.getValue()));
        ofNullable(request.getUpdated().getAddressType()).ifPresent(at -> write(requestPayload, REQUEST_ADDRESS_TYPE.value(), at.getName()));
        return requestPayload;
      }).collect(Collectors.toList())
    );
  }
}
