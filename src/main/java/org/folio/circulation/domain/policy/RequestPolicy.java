package org.folio.circulation.domain.policy;

import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.json.JsonStringArrayPropertyFetcher;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

public class RequestPolicy {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Getter
  private final String id;

  private final List<String> requestTypes;

  private RequestPolicy(String id, List<String> requestTypes) {
    this.id = id;
    this.requestTypes = requestTypes;
  }

  public static RequestPolicy from(JsonObject representation) {
    log.debug("from:: parameters representation: {}", asJson(representation));
    return new RequestPolicy(representation.getString("id"),
      JsonStringArrayPropertyFetcher
        .toStream(representation, "requestTypes")
        .collect(Collectors.toList()));
  }

  public boolean allowsType(RequestType type) {
    for (String requestType : requestTypes) {
      if (type.nameMatches(requestType))
        return true;
    }
    return false;
  }
}
