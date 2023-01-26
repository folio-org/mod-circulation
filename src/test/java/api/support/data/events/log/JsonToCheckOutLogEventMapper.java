package api.support.data.events.log;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class JsonToCheckOutLogEventMapper {
  public CheckOutLogEvent fromJson(JsonObject json) {
    final var changedRequestMapper = new JsonToChangedRequestMapper();

    return CheckOutLogEvent.builder()
      .loanId(getProperty(json, "loanId"))
      .changedRequests(changedRequestMapper.fromJson(getArrayProperty(json, "requests")))
      .build();
  }
}
