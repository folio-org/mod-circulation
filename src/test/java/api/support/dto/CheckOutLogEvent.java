package api.support.dto;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class CheckOutLogEvent {
  public final String loanId;
  public final List<ChangedRequest> changedRequests;

  public ChangedRequest firstChangedRequest() {
    return changedRequests.get(0);
  }

  public static CheckOutLogEvent fromJson(JsonObject json) {
    return CheckOutLogEvent.builder()
      .loanId(getProperty(json, "loanId"))
      .changedRequests(changedRequestsFromJson(json))
      .build();
  }

  private static List<ChangedRequest> changedRequestsFromJson(JsonObject json) {
    return mapToList(json, "requests", ChangedRequest::fromJson);
  }
}
