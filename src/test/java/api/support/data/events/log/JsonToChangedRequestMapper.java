package api.support.data.events.log;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonToChangedRequestMapper {
  ChangedRequest fromJson(JsonObject json) {
    return ChangedRequest.builder()
      .id(getProperty(json, "id"))
      .requestType(getProperty(json, "requestType"))
      .oldRequestStatus(getProperty(json, "oldRequestStatus"))
      .newRequestStatus(getProperty(json, "newRequestStatus"))
      .build();
  }

  List<ChangedRequest> fromJson(JsonArray array) {
    return mapToList(array, this::fromJson);
  }
}
