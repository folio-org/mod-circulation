package api.support.data.events.log;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class JsonToChangedRequestMapper {
  public ChangedRequest fromJson(JsonObject json) {
    return ChangedRequest.builder()
      .id(getProperty(json, "id"))
      .requestType(getProperty(json, "requestType"))
      .oldRequestStatus(getProperty(json, "oldRequestStatus"))
      .newRequestStatus(getProperty(json, "newRequestStatus"))
      .build();
  }
}
