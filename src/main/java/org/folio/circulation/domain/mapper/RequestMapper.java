package org.folio.circulation.domain.mapper;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;

public class RequestMapper {

  private RequestMapper() {
  }

  public static JsonObject createRequestContext(Request request) {
    Optional<Request> optionalRequest = Optional.ofNullable(request);
    JsonObject requestContext = new JsonObject();

    optionalRequest
      .map(Request::getId)
      .ifPresent(value -> requestContext.put("requestID", value));
    optionalRequest
      .map(Request::getPickupServicePoint)
      .map(ServicePoint::getName)
      .ifPresent(value -> requestContext.put("servicePointPickup", value));
    optionalRequest
      .map(Request::getRequestDate)
      .ifPresent(value -> write(requestContext, "requestDate", value));
    optionalRequest
      .map(Request::getRequestExpirationDate)
      .ifPresent(value -> write(requestContext, "requestExpirationDate", value));
    optionalRequest
      .map(Request::getHoldShelfExpirationDate)
      .ifPresent(value -> write(requestContext, "holdShelfExpirationDate", value));
    optionalRequest
      .map(Request::getCancellationAdditionalInformation)
      .ifPresent(value -> requestContext.put("additionalInfo", value));
    optionalRequest
      .map(Request::getCancellationReasonPublicDescription)
      .map(Optional::of)
      .orElse(optionalRequest.map(Request::getCancellationReasonName))
      .ifPresent(value -> requestContext.put("reasonForCancellation", value));
    optionalRequest
      .map(Request::getAddressType)
      .ifPresent(value -> requestContext.put("deliveryAddressType", value.getName()));
    optionalRequest
      .map(Request::getPatronComments)
      .ifPresent(value -> write(requestContext, "patronComments", value));

    return requestContext;
  }
}
