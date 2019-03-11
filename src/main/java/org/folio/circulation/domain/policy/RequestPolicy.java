package org.folio.circulation.domain.policy;

import java.util.ArrayList;

import org.folio.circulation.domain.RequestType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestPolicy {

  private ArrayList<String> requestTypes;
  private final JsonObject representation;

  public RequestPolicy(JsonObject representation){
    this.representation = representation;
    populateRequestTypes();
  }

  static RequestPolicy from(JsonObject representation) {
    return new RequestPolicy(representation);
  }

  public boolean containsType(RequestType type){
    for (String requestType : requestTypes) {
      if (type.nameMatches(requestType))
        return true;
    }
    return false;
  }

  private void populateRequestTypes(){

    requestTypes = new ArrayList<>();
    JsonArray requestTypesJson = representation.getJsonArray("requestTypes");

    for ( Object type : requestTypesJson) {
      this.requestTypes.add(type.toString());
    }
  }
}
