package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.lang.invoke.MethodHandles;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.json.JsonStringArrayPropertyFetcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

public class RequestPolicy {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Getter
  private final String id;

  private final List<String> requestTypes;
  @Getter
  private final Map<RequestType, Set<UUID>> allowedServicePoints;

  private RequestPolicy(String id, List<String> requestTypes,
    Map<RequestType, Set<UUID>> allowedServicePoints) {
    this.id = id;
    this.requestTypes = requestTypes;
    this.allowedServicePoints = allowedServicePoints;
  }

  public static RequestPolicy from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", representation);

    Map<RequestType, Set<UUID>> allowedServicePoints = extractAllowedServicePoints(
      representation.getJsonObject("allowedServicePoints"));

    return new RequestPolicy(representation.getString("id"),
      JsonStringArrayPropertyFetcher
        .toStream(representation, "requestTypes")
        .toList(), allowedServicePoints);
  }

  public boolean allowsType(RequestType type) {
    log.debug("allowsType:: parameters: type: {}", type);
    for (String requestType : requestTypes) {
      if (type.nameMatches(requestType)) {
        log.info("allowsType:: result: true");
        return true;
      }
    }
    log.info("allowsType:: result: false");
    return false;
  }

  private static Map<RequestType, Set<UUID>> extractAllowedServicePoints(
    JsonObject allowedServicePointsJson) {

    log.debug("extractAllowedServicePoints:: parameters representation: {}",
      allowedServicePointsJson);

    Map<RequestType, Set<UUID>> allowedServicePoints = new EnumMap<>(RequestType.class);
    if (allowedServicePointsJson != null) {
      for (RequestType requestType : RequestType.values()) {
        JsonArray jsonArray = allowedServicePointsJson.getJsonArray(requestType.getValue());
        if (jsonArray != null) {
          Set<UUID> servicePointIds = jsonArray.stream()
            .map(String.class::cast)
            .map(UUID::fromString)
            .collect(Collectors.toSet());
          allowedServicePoints.put(requestType, servicePointIds);
        }
      }
    }
    log.info("extractAllowedServicePoints:: result: {}", () -> asJson(allowedServicePoints));

    return allowedServicePoints;
  }
}
