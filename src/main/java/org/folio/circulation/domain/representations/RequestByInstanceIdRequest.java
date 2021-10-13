package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RequestByInstanceIdRequest {
  private static final String REQUEST_DATE = "requestDate";
  private static final String REQUESTER_ID = "requesterId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  private static final String PICKUP_SERVICE_POINT_ID = "pickupServicePointId";

  private final ZonedDateTime requestDate;
  private final UUID requesterId;
  private final UUID instanceId;
  private final ZonedDateTime requestExpirationDate;
  private final UUID pickupServicePointId;
  private final String patronComments;

  public static Result<RequestByInstanceIdRequest> from(JsonObject json) {
    final ZonedDateTime requestDate = getDateTimeProperty(json, REQUEST_DATE);

    if (requestDate == null) {
      return failedValidation("Request must have a request date", REQUEST_DATE, null);
    }

    final UUID requesterId = getUUIDProperty(json, REQUESTER_ID);

    if (requesterId == null) {
      return failedValidation("Request must have a requester id", REQUESTER_ID, null);
    }

    final UUID instanceId = getUUIDProperty(json, INSTANCE_ID);

    if (instanceId == null) {
      return failedValidation("Request must have an instance id", INSTANCE_ID, null);
    }

    final ZonedDateTime requestExpirationDate = getDateTimeProperty(json, REQUEST_EXPIRATION_DATE);
    final UUID pickupServicePointId = getUUIDProperty(json, PICKUP_SERVICE_POINT_ID);

    return succeeded(new RequestByInstanceIdRequest(requestDate, requesterId, instanceId,
        requestExpirationDate, pickupServicePointId, getProperty(json, "patronComments")));
  }
}
