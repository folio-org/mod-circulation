package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.RequestFulfillmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.representations.RequestProperties.PICKUP_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.ErrorCode.INSTANCE_ALREADY_REQUESTED;
import static org.folio.circulation.support.ErrorCode.ITEM_ALREADY_REQUESTED;
import static org.folio.circulation.support.ErrorCode.ITEM_OF_THIS_INSTANCE_ALREADY_REQUESTED;
import static org.folio.circulation.support.ErrorCode.MOVING_REQUEST_TO_THE_SAME_ITEM;
import static org.folio.circulation.support.ErrorCode.REQUEST_NOT_ALLOWED_FOR_PATRON_ITEM_COMBINATION;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request.Operation;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class RequestServiceUtility {
  private static final String INSTANCE_ID = "instanceId";
  private static final String ITEM_ID = "itemId";
  private static final String REQUESTER_ID = "requesterId";
  private static final String REQUEST_ID = "requestId";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RequestServiceUtility() { }

  static Result<RequestAndRelatedRecords> refuseWhenInstanceDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenInstanceDoesNotExist:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    if (requestAndRelatedRecords.getRequest().getInstance().isNotFound()) {
      String instanceId = requestAndRelatedRecords.getRequest().getInstanceId();
      log.error("refuseWhenInstanceDoesNotExist:: instance {} does not exist",
        instanceId);
      return failedValidation("Instance does not exist", INSTANCE_ID, instanceId);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenItemDoesNotExist:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    if (requestAndRelatedRecords.getRequest().getItem().isNotFound()) {
      String itemId = requestAndRelatedRecords.getRequest().getItemId();
      log.error("refuseWhenItemDoesNotExist:: item {} does not exist", itemId);
      return failedValidation("Item does not exist", ITEM_ID,
        requestAndRelatedRecords.getRequest().getItemId());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenRequestCannotBeFulfilled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenRequestCannotBeFulfilled:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    RequestPolicy requestPolicy = requestAndRelatedRecords.getRequestPolicy();
    Request request = requestAndRelatedRecords.getRequest();
    RequestType requestType = request.getRequestType();

    if (!requestPolicy.allowsType(requestType)) {
      log.warn("refuseWhenRequestCannotBeFulfilled:: requestPolicy does not allow " +
        "the requestType {}", requestType);
      return failureDisallowedForRequestType(requestType);
    }

    if (HOLD_SHELF == request.getfulfillmentPreference() && !requestPolicy.allowsServicePoint(
      requestType, request.getPickupServicePointId())) {

        log.warn("refuseWhenRequestCannotBeFulfilled:: requestPolicy does not allow servicePoint {}",
          request.getPickupServicePointId());
        return failedValidation("One or more Pickup locations are no longer available",
          Map.of(PICKUP_SERVICE_POINT_ID, request.getPickupServicePointId(),
            REQUEST_TYPE, requestType.toString(),
            "requestPolicyId", requestPolicy.getId()),
          ErrorCode.REQUEST_PICKUP_SERVICE_POINT_IS_NOT_ALLOWED);
      }

    return succeeded(requestAndRelatedRecords);

  }

  static Result<RequestAndRelatedRecords> refuseWhenRequestTypeIsNotAllowedForItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenRequestTypeIsNotAllowedForItem:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    Request request = requestAndRelatedRecords.getRequest();

    if (request.getItem().isNotFound() || request.allowedForItem()) {
      return succeeded(requestAndRelatedRecords);
    } else {
      log.error("refuseWhenRequestTypeIsNotAllowedForItem:: {} is not allowed for item {}",
        request.getRequestType(), request.getItem().getItemId());
      return failureDisallowedForRequestType(request.getRequestType());
    }
  }

  private static Result<RequestAndRelatedRecords> failureDisallowedForRequestType(
    RequestType requestType) {

    final String requestTypeName = requestType.getValue();

    return failedValidation(
      format("%s requests are not allowed for this patron and item combination", requestTypeName),
      REQUEST_TYPE, requestTypeName, REQUEST_NOT_ALLOWED_FOR_PATRON_ITEM_COMBINATION);
  }

  static Result<RequestAndRelatedRecords> refuseWhenInvalidUserAndPatronGroup(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    User requester = request.getRequester();

    if (requester == null) {
      log.error("refuseWhenInvalidUserAndPatronGroup:: user {} is null", request.getUserId());
      return failedValidation("A valid user and patron group are required. User is null", "userId",
        request.getUserId());
    } else if (requester.getPatronGroupId() == null) {
      log.error("refuseWhenInvalidUserAndPatronGroup:: patronGroup is null");
      return failedValidation("A valid patron group is required. PatronGroup ID is null", "PatronGroupId", null);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenUserIsInactive(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenUserIsInactive:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    Request request = requestAndRelatedRecords.getRequest();
    User requester = request.getRequester();

    if (requester != null && requester.isInactive()) {
      Map<String, String> parameters = new HashMap<>();

      parameters.put(REQUESTER_ID, request.getUserId());
      parameters.put(ITEM_ID, request.getItemId());

      String message = "Inactive users cannot make requests";
      log.warn("refuseWhenUserIsInactive:: user {} is inactive", requester.getId());
      return failedValidation(new ValidationError(message, parameters));
    } else {
      return of(() -> requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenMovedToDifferentInstance(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenMovedToDifferentInstance:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    Request request = requestAndRelatedRecords.getRequest();
    Item item = request.getItem();
    if (!Objects.equals(item.getInstanceId(), request.getInstanceId())) {
      HashMap<String, String> parameters = new HashMap<>();
      parameters.put(ITEM_ID, request.getItemId());
      parameters.put(INSTANCE_ID, item.getInstanceId());
      String message = "Request can only be moved to an item with the same instance ID";
      log.warn("refuseWhenMovedToDifferentInstance:: requested instance ID {}" +
        "does not match item's instance ID {}", request.getInstanceId(), item.getInstanceId());
      return failedValidation(new ValidationError(message, parameters));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseTlrProcessingWhenFeatureIsDisabled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseTlrProcessingWhenFeatureIsDisabled:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);
    Request request = requestAndRelatedRecords.getRequest();
    if (!requestAndRelatedRecords.isTlrFeatureEnabled() && request.isTitleLevel()) {
      String message = "Can not process TLR - TLR feature is disabled";
      log.warn("refuseTlrProcessingWhenFeatureIsDisabled:: {}", message);
      return failedValidation(new ValidationError(message, REQUEST_ID, request.getId()));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseMovingToOrFromHoldTlr(
    RequestAndRelatedRecords requestAndRelatedRecords, Request originalRequest) {

    log.debug("refuseMovingToOrFromHoldTlr:: parameters requestAndRelatedRecords: {}, " +
      "originalRequest: {}", requestAndRelatedRecords, originalRequest);
    Request request = requestAndRelatedRecords.getRequest();
    if ((request.isHold() && request.isTitleLevel())
      || (originalRequest.isHold() && originalRequest.isTitleLevel())) {

      String message = "Not allowed to move from/to Hold TLR";
      log.warn("refuseMovingToOrFromHoldTlr:: {}", message);

      return failedValidation(new ValidationError(message, REQUEST_ID, request.getId()));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseWhenAlreadyRequested(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenAlreadyRequested:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);

    return requestAndRelatedRecords.getRequestQueue().getRequests().stream()
      .filter(isAlreadyRequested(requestAndRelatedRecords))
      .findFirst()
      .map(existingRequest -> alreadyRequestedFailure(requestAndRelatedRecords, existingRequest))
      .orElse(of(() -> requestAndRelatedRecords));
  }

  private static Predicate<Request> isAlreadyRequested(RequestAndRelatedRecords records) {
    log.debug("isAlreadyRequested:: parameters records: {}", () -> records);
    Request request = records.getRequest();
    if (records.isTlrFeatureEnabled() && request.isTitleLevel()) {
      return req -> {
        if (request.getOperation() != Operation.MOVE) {
          return isTheSameRequester(records, req) && req.isOpen();
        }

        return isTheSameRequester(records, req) && req.isOpen() && Objects.equals(req.getItemId(),
          request.getItemId());
      };
    } else {
      return req -> {
        if (req.isTitleLevel() && records.isTlrFeatureEnabled()) {
          return request.getInstanceId().equals(req.getInstanceId())
            && isTheSameRequester(records, req) && req.isOpen();
        }
        return records.getItemId().equals(req.getItemId())
            && isTheSameRequester(records, req) && req.isOpen();
      };
    }
  }

  private static Result<RequestAndRelatedRecords> alreadyRequestedFailure(
    RequestAndRelatedRecords requestAndRelatedRecords, Request existingRequest) {

    log.debug("alreadyRequestedFailure:: parameters requestAndRelatedRecords: {}, " +
      "existingRequest: {}", () -> requestAndRelatedRecords, () -> existingRequest);
    Request requestBeingPlaced = requestAndRelatedRecords.getRequest();
    HashMap<String, String> parameters = new HashMap<>();
    String message;
    ErrorCode errorCode;

    if (requestBeingPlaced.isTitleLevel()) {
      if (existingRequest.isTitleLevel()) {
        parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
        parameters.put(INSTANCE_ID, requestBeingPlaced.getInstanceId());

        if (requestBeingPlaced.getOperation() == Operation.MOVE) {
          message = "Not allowed to move title level page request to the same item";
          errorCode = MOVING_REQUEST_TO_THE_SAME_ITEM;
        } else {
          message = "This requester already has an open request for this instance";
          errorCode = INSTANCE_ALREADY_REQUESTED;
        }
      } else {
        parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
        parameters.put(INSTANCE_ID, requestBeingPlaced.getInstanceId());

        message = "This requester already has an open request for one of the instance's items";
        errorCode = ITEM_OF_THIS_INSTANCE_ALREADY_REQUESTED;
      }
    } else {
      parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
      parameters.put(ITEM_ID, requestBeingPlaced.getItemId());
      parameters.put(REQUEST_ID, requestBeingPlaced.getId());

      message = "This requester already has an open request for this item";
      errorCode = ITEM_ALREADY_REQUESTED;
    }
    log.info("alreadyRequestedFailure:: message: {}, errorCode: {}", message, errorCode);

    return failedValidation(message, parameters, errorCode);
  }

  static boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }
}
