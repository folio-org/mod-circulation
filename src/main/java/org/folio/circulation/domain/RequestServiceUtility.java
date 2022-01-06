package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
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

  static Result<RequestAndRelatedRecords> refuseWhenAlreadyRequested(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();

    Predicate<Request> isAlreadyRequested;
    Function<Request, Result<RequestAndRelatedRecords>> alreadyRequestedErrorMapper;

    if (isTlrEnabled(request)) {
      alreadyRequestedErrorMapper = (request.getRequestLevel() == TITLE)
        ? req -> requestLevelTitleAndTlrEnabledErrorMapper(requestAndRelatedRecords)
        : req -> requestLevelItemAndTlrEnabledErrorMapper(requestAndRelatedRecords);
      isAlreadyRequested = req -> isTheSameRequester(requestAndRelatedRecords, req);
    } else {
      alreadyRequestedErrorMapper = req -> requestLevelItemAndTlrDisabledErrorMapper(requestAndRelatedRecords);
      isAlreadyRequested = req -> requestAndRelatedRecords.getItemId().equals(req.getItemId())
        && isTheSameRequester(requestAndRelatedRecords, req) && req.isOpen();
    }

    return requestAndRelatedRecords.getRequestQueue().getRequests().stream()
      .filter(isAlreadyRequested)
      .findFirst()
      .map(alreadyRequestedErrorMapper)
      .orElse(of(() -> requestAndRelatedRecords));
  }

  private static boolean isTlrEnabled(Request request) {
    TlrSettingsConfiguration tlrSettingsConfiguration = request.getTlrSettingsConfiguration();
    return tlrSettingsConfiguration != null
      && tlrSettingsConfiguration.isTitleLevelRequestsFeatureEnabled();
  }

  static boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }

  private static Result<RequestAndRelatedRecords> requestLevelTitleAndTlrEnabledErrorMapper(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    HashMap<String, String> parameters = new HashMap<>();
    parameters.put(REQUESTER_ID, requestAndRelatedRecords.getRequest().getUserId());
    parameters.put(INSTANCE_ID, requestAndRelatedRecords.getRequest().getInstanceId());

    return failedValidation("This requester already has an open request for this instance", parameters);
  }

  private static Result<RequestAndRelatedRecords> requestLevelItemAndTlrEnabledErrorMapper(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    HashMap<String, String> parameters = new HashMap<>();
    parameters.put(REQUESTER_ID, requestAndRelatedRecords.getRequest().getUserId());
    parameters.put(ITEM_ID, requestAndRelatedRecords.getRequest().getItemId());
    parameters.put(INSTANCE_ID, requestAndRelatedRecords.getRequest().getInstanceId());

    return failedValidation("This requester already has an open request for one of the instance's items", parameters);
  }

  private static Result<RequestAndRelatedRecords> requestLevelItemAndTlrDisabledErrorMapper(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Map<String, String> parameters = new HashMap<>();
    parameters.put(REQUESTER_ID, requestAndRelatedRecords.getRequest().getUserId());
    parameters.put(ITEM_ID, requestAndRelatedRecords.getRequest().getItemId());
    parameters.put(REQUEST_ID, requestAndRelatedRecords.getRequest().getId());

    return failedValidation("This requester already has an open request for this item", parameters);
  }

}
