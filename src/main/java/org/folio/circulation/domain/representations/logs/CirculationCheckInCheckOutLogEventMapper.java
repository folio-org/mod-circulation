package org.folio.circulation.domain.representations.logs;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.*;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_OUT;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.LogUtil.listAsString;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.UpdatedRequestPair;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CirculationCheckInCheckOutLogEventMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String PAYLOAD = "payload";

  private CirculationCheckInCheckOutLogEventMapper() {
  }

  /**
   * This method returns {@link String} content for check-in log event payload
   * @param checkInContext check-in flow context {@link CheckInContext}
   * @return check-in log event payload
   */
  public static String mapToCheckInLogEventContent(CheckInContext checkInContext,
    User loggedInUser, User userFromLastLoan) {

    log.debug("mapToCheckInLogEventContent:: parameters checkInContext: {}, loggedInUser: {}, " +
      "userFromLastLoan: {}", checkInContext, loggedInUser, userFromLastLoan);

    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), CHECK_IN.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), checkInContext.getCheckInServicePointId());

    populateLoanData(checkInContext, logEventPayload, userFromLastLoan);
    populateItemData(checkInContext, logEventPayload, loggedInUser);

    ofNullable(checkInContext.getCheckInRequest())
      .flatMap(checkInRequest -> ofNullable(checkInRequest.getClaimedReturnedResolution()))
      .ifPresent(resolution -> write(logEventPayload, CLAIMED_RETURNED_RESOLUTION.value(), resolution.getValue()));

    write(logEventPayload, REQUESTS.value(), getUpdatedRequests(checkInContext));

    String result = logEventPayload.encode();
    log.info("mapToCheckInLogEventContent:: result {}", result);
    return result;
  }

  /**
   * This method returns {@link String} content for check-out log event payload
   * @param loanAndRelatedRecords check-out flow context {@link LoanAndRelatedRecords}
   * @return check-out log event payload
   */
  public static String mapToCheckOutLogEventContent(LoanAndRelatedRecords loanAndRelatedRecords,
    User loggedInUser) {

    log.debug("mapToCheckOutLogEventContent:: parameters loanAndRelatedRecords: {}, " +
      "loggedInUser: {}", loanAndRelatedRecords, loggedInUser);

    JsonObject logEventPayload = new JsonObject();
    JsonObject payload = new JsonObject();

    var logEventType = loanAndRelatedRecords.getLoan().getAction().equalsIgnoreCase(LoanAction.CHECKED_OUT_THROUGH_OVERRIDE.getValue()) ? CHECK_OUT_THROUGH_OVERRIDE : CHECK_OUT;

    write(logEventPayload, LOG_EVENT_TYPE.value(), logEventType.value());

    write(payload, SERVICE_POINT_ID.value(), loanAndRelatedRecords.getLoan().getCheckoutServicePointId());
    write(logEventPayload, SERVICE_POINT_ID.value(), loanAndRelatedRecords.getLoan().getCheckoutServicePointId());

    populateLoanAndItemInCheckoutEvent(loanAndRelatedRecords, loggedInUser, payload);
    populateLoanAndItemInCheckoutEvent(loanAndRelatedRecords, loggedInUser, logEventPayload);

    write(logEventPayload, REQUESTS.value(), getUpdatedRequests(loanAndRelatedRecords));
    logEventPayload.put(PAYLOAD,payload);

    String result = logEventPayload.encode();
    log.info("mapToCheckOutLogEventContent:: result {}", result);
    return result;
  }

  private static void populateLoanAndItemInCheckoutEvent(LoanAndRelatedRecords loanAndRelatedRecords, User loggedInUser, JsonObject data) {
    populateLoanData(loanAndRelatedRecords, data);
    populateItemData(loanAndRelatedRecords, data, loggedInUser);
  }

  private static void populateItemData(CheckInContext checkInContext, JsonObject logEventPayload,
    User loggedInUser) {

    log.debug("populateItemData:: parameters checkInContext: {}, logEventPayload: {}, " +
      "loggedInUser: {}", checkInContext, logEventPayload, loggedInUser);

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

  private static void populateItemData(LoanAndRelatedRecords loanAndRelatedRecords,
    JsonObject logEventPayload, User loggedInUser) {

    log.debug("populateItemData:: parameters loanAndRelatedRecords: {}, logEventPayload: {}, " +
      "loggedInUser: {}", loanAndRelatedRecords, logEventPayload, loggedInUser);

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

  private static void populateLoanData(CheckInContext checkInContext, JsonObject logEventPayload, User userFromLastLoan) {
    if (nonNull(checkInContext.getLoan())) {
      populateLoanData(checkInContext.getLoan(), logEventPayload);
    } else {
      enrichWithUserBarcode(logEventPayload, userFromLastLoan);
    }
  }

  private static void populateLoanData(LoanAndRelatedRecords loanAndRelatedRecords, JsonObject logEventPayload) {
    populateLoanData(loanAndRelatedRecords.getLoan(), logEventPayload);
  }

  private static void populateLoanData(Loan checkInCheckOutLoan, JsonObject logEventPayload) {
    log.debug("populateLoanData:: parameters checkInCheckOutLoan: {}, logEventPayload: {}",
      checkInCheckOutLoan, logEventPayload);

    write(logEventPayload, LOAN_ID.value(), checkInCheckOutLoan.getId());
    write(logEventPayload, IS_LOAN_CLOSED.value(), checkInCheckOutLoan.isClosed());
    write(logEventPayload, SYSTEM_RETURN_DATE.value(), checkInCheckOutLoan.getSystemReturnDate());
    write(logEventPayload, RETURN_DATE.value(), checkInCheckOutLoan.getReturnDate());
    write(logEventPayload, DUE_DATE.value(), checkInCheckOutLoan.getDueDate());
    ofNullable(checkInCheckOutLoan.getUser())
      .ifPresent(user -> {
        write(logEventPayload, USER_ID.value(), user.getId());
        write(logEventPayload, USER_BARCODE.value(), user.getBarcode());
      });
    ofNullable(checkInCheckOutLoan.getProxy())
      .ifPresent(proxy -> write(logEventPayload, PROXY_BARCODE.value(), proxy.getBarcode()));
  }

  private static void enrichWithUserBarcode(JsonObject logEventPayload, User userFromLastLoan) {
    ofNullable(userFromLastLoan).ifPresent(user -> write(logEventPayload, USER_BARCODE.value(), userFromLastLoan.getBarcode()));
  }

  private static JsonArray getUpdatedRequests(CheckInContext checkInContext) {
    return mapUpdatedRequestPairsToJsonArray(checkInContext.getRequestQueue().getUpdatedRequests());
  }

  private static JsonArray getUpdatedRequests(LoanAndRelatedRecords loanAndRelatedRecords) {
    return mapUpdatedRequestPairsToJsonArray(loanAndRelatedRecords.getRequestQueue().getUpdatedRequests());
  }

  private static JsonArray mapUpdatedRequestPairsToJsonArray(
    List<UpdatedRequestPair> updatedRequestPairs) {

    log.debug("mapUpdatedRequestPairsToJsonArray:: parameters updatedRequestPairs: {}",
      () -> listAsString(updatedRequestPairs));

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
