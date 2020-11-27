package api.support.data.events.log;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;

import io.vertx.core.json.JsonObject;

public class JsonToCheckOutLogEventMapper {
  public CheckOutLogEvent fromJson(JsonObject json) {
    return CheckOutLogEvent.builder()
      .loanId(getProperty(json, "loanId"))
      .changedRequests(changedRequestsFromJson(json))
      .build();
  }

  private List<ChangedRequest> changedRequestsFromJson(JsonObject json) {
    return mapToList(getArrayProperty(json, "requests"), new JsonToChangedRequestMapper()::fromJson);
  }
}
