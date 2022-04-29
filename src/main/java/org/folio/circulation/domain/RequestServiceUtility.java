package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.folio.circulation.domain.Request.Operation;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class RequestServiceUtility {
  private static final String INSTANCE_ID = "instanceId";
  private static final String ITEM_ID = "itemId";
  private static final String REQUESTER_ID = "requesterId";
  private static final String REQUEST_ID = "requestId";

  private RequestServiceUtility() { }

  static Result<RequestAndRelatedRecords> refuseWhenInstanceDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if (requestAndRelatedRecords.getRequest().getInstance().isNotFound()) {
      return failedValidation("Instance does not exist", INSTANCE_ID,
        requestAndRelatedRecords.getRequest().getInstanceId());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenItemDoesNotExist(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if (requestAndRelatedRecords.getRequest().getItem().isNotFound()) {
      return failedValidation("Item does not exist", ITEM_ID,
        requestAndRelatedRecords.getRequest().getItemId());
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenRequestCannotBeFulfilled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestPolicy requestPolicy = requestAndRelatedRecords.getRequestPolicy();
    RequestType requestType = requestAndRelatedRecords.getRequest().getRequestType();

    if (!requestPolicy.allowsType(requestType)) {
      return failureDisallowedForRequestType(requestType);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenRequestTypeIsNotAllowedForItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();

    if (request.getItem().isNotFound() || request.allowedForItem()) {
      return succeeded(requestAndRelatedRecords);
    } else {
      return failureDisallowedForRequestType(request.getRequestType());
    }
  }

  private static Result<RequestAndRelatedRecords> failureDisallowedForRequestType(
    RequestType requestType) {

    final String requestTypeName = requestType.getValue();

    return failedValidation(
      format("%s requests are not allowed for this patron and item combination", requestTypeName),
      REQUEST_TYPE, requestTypeName);
  }

  static Result<RequestAndRelatedRecords> refuseWhenInvalidUserAndPatronGroup(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    User requester = request.getRequester();

    if (requester == null) {
      return failedValidation("A valid user and patron group are required. User is null", "userId",
        request.getUserId());
    } else if (requester.getPatronGroupId() == null) {
      return failedValidation("A valid patron group is required. PatronGroup ID is null", "PatronGroupId", null);
    } else {
      return succeeded(requestAndRelatedRecords);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenUserIsInactive(
    RequestAndRelatedRecords request) {

    User requester = request.getRequest().getRequester();

    if (requester != null && requester.isInactive()) {
      Map<String, String> parameters = new HashMap<>();

      parameters.put(REQUESTER_ID, request.getRequest().getUserId());
      parameters.put(ITEM_ID, request.getRequest().getItemId());

      String message = "Inactive users cannot make requests";

      return failedValidation(new ValidationError(message, parameters));
    } else {
      return of(() -> request);
    }
  }

  static Result<RequestAndRelatedRecords> refuseWhenMovedToDifferentInstance(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    Item item = request.getItem();
    if (!Objects.equals(item.getInstanceId(), request.getInstanceId())) {
      HashMap<String, String> parameters = new HashMap<>();
      parameters.put(ITEM_ID, request.getItemId());
      parameters.put(INSTANCE_ID, item.getInstanceId());
      return failedValidation(
        new ValidationError("Request can only be moved to an item with the same instance ID",
          parameters));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseTlrProcessingWhenFeatureIsDisabled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    if (!requestAndRelatedRecords.isTlrFeatureEnabled() && request.isTitleLevel()) {
      return failedValidation(
        new ValidationError("Can not process TLR's with TLR feature disabled",
          REQUEST_ID, request.getId()));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseMovingToOrFromHoldTlr(
    RequestAndRelatedRecords requestAndRelatedRecords, Request originalRequest) {

    Request request = requestAndRelatedRecords.getRequest();
    if ((request.isHold() && request.isTitleLevel()) || (originalRequest.isHold() &&
      originalRequest.isTitleLevel())) {
      return failedValidation(
        new ValidationError("Moving from/to Hold TLR is disallowed",
          REQUEST_ID, request.getId()));
    }

    return succeeded(requestAndRelatedRecords);
  }

  static Result<RequestAndRelatedRecords> refuseWhenAlreadyRequested(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return requestAndRelatedRecords.getRequestQueue().getRequests().stream()
      .filter(isAlreadyRequested(requestAndRelatedRecords))
      .findFirst()
      .map(existingRequest -> alreadyRequestedFailure(requestAndRelatedRecords, existingRequest))
      .orElse(of(() -> requestAndRelatedRecords));
  }

  private static Predicate<Request> isAlreadyRequested(RequestAndRelatedRecords records) {
    Request request = records.getRequest();
    if (records.isTlrFeatureEnabled() && request.isTitleLevel()) {
      return req -> isTheSameRequester(records, req) && req.isOpen() && request.getOperation() != Operation.MOVE;
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

    Request requestBeingPlaced = requestAndRelatedRecords.getRequest();
    HashMap<String, String> parameters = new HashMap<>();
    String message;

    if (requestBeingPlaced.isTitleLevel()) {
      if (existingRequest.isTitleLevel()) {
        parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
        parameters.put(INSTANCE_ID, requestBeingPlaced.getInstanceId());

        message = "This requester already has an open request for this instance";
      } else {
        parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
        parameters.put(INSTANCE_ID, requestBeingPlaced.getInstanceId());

        message = "This requester already has an open request for one of the instance's items";
      }
    } else {
      parameters.put(REQUESTER_ID, requestBeingPlaced.getUserId());
      parameters.put(ITEM_ID, requestBeingPlaced.getItemId());
      parameters.put(REQUEST_ID, requestBeingPlaced.getId());

      message = "This requester already has an open request for this item";
    }

    return failedValidation(message, parameters);
  }

  static boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }
}
