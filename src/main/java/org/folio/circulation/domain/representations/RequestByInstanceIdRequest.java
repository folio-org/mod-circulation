package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class RequestByInstanceIdRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String REQUEST_DATE = "requestDate";
  private static final String REQUESTER_ID = "requesterId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  private static final String PICKUP_SERVICE_POINT_ID = "pickupServicePointId";
  private static final String REQUEST_LEVEL = "requestLevel";

  @ToString.Include
  private final ZonedDateTime requestDate;
  @ToString.Include
  private final UUID requesterId;
  @ToString.Include
  private final UUID instanceId;
  private final ZonedDateTime requestExpirationDate;
  @ToString.Include
  private final UUID pickupServicePointId;
  private final String patronComments;
  @ToString.Include
  private final String requestLevel;

  public static Result<RequestByInstanceIdRequest> from(JsonObject json) {
    log.debug("from:: parameters json={}", json);

    final ZonedDateTime requestDate = getDateTimeProperty(json, REQUEST_DATE);

    if (requestDate == null) {
      log.info("from:: requestDate is null");
      return failedValidation("Request must have a request date", REQUEST_DATE, null);
    }

    final UUID requesterId = getUUIDProperty(json, REQUESTER_ID);

    if (requesterId == null) {
      log.info("from:: requesterId is null");
      return failedValidation("Request must have a requester id", REQUESTER_ID, null);
    }

    final UUID instanceId = getUUIDProperty(json, INSTANCE_ID);

    if (instanceId == null) {
      log.info("from:: instanceId is null");
      return failedValidation("Request must have an instance id", INSTANCE_ID, null);
    }

    final ZonedDateTime requestExpirationDate = getDateTimeProperty(json, REQUEST_EXPIRATION_DATE);
    final UUID pickupServicePointId = getUUIDProperty(json, PICKUP_SERVICE_POINT_ID);
    final String requestLevel = json.getString(REQUEST_LEVEL);

    Result<RequestByInstanceIdRequest> result = succeeded(new RequestByInstanceIdRequest(
      requestDate, requesterId, instanceId, requestExpirationDate, pickupServicePointId,
      getProperty(json, "patronComments"), requestLevel));

    log.info("from:: result {}", () -> resultAsString(result));
    return result;
  }
}
