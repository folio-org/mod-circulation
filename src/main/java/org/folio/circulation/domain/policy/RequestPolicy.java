package org.folio.circulation.domain.policy;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.json.JsonStringArrayPropertyFetcher;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

public class RequestPolicy {
  @Getter
  private final String id;

  private final List<String> requestTypes;

  private RequestPolicy(String id, List<String> requestTypes) {
    this.id = id;
    this.requestTypes = requestTypes;
  }

  public static RequestPolicy from(JsonObject representation) {
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
