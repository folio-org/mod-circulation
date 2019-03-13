package org.folio.circulation.domain.policy;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.JsonStringArrayHelper;

import io.vertx.core.json.JsonObject;

public class RequestPolicy {

  private List<String> requestTypes;
  private final JsonObject representation;

  public RequestPolicy(JsonObject representation){
    this.representation = representation;
    this.requestTypes = JsonStringArrayHelper
      .toStream(representation, "requestTypes")
      .collect(Collectors.toList());
  }

  static RequestPolicy from(JsonObject representation) {
    return new RequestPolicy(representation);
  }

  public boolean allowsType(RequestType type){
    for (String requestType : requestTypes) {
      if (type.nameMatches(requestType))
        return true;
    }
    return false;
  }
}
