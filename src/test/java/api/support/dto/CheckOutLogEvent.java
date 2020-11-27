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
  public static CheckOutLogEvent fromJson(JsonObject json) {
    return CheckOutLogEvent.builder()
      .loanId(getProperty(json, "loanId"))
      .changedRequests(changedRequestsFromJson(json))
      .build();
  }

  private static List<CheckOutLogEventChangedRequest> changedRequestsFromJson(JsonObject json) {
    return mapToList(json, "requests",
      request -> CheckOutLogEventChangedRequest.builder()
        .id(getProperty(request, "id"))
        .requestType(getProperty(request, "requestType"))
        .oldRequestStatus(getProperty(request, "oldRequestStatus"))
        .newRequestStatus(getProperty(request, "newRequestStatus"))
        .build());
  }

  public final String loanId;
  public final List<CheckOutLogEventChangedRequest> changedRequests;

  @AllArgsConstructor
  @Builder
  public static class CheckOutLogEventChangedRequest {
    public final String id;
    public final String requestType;
    public final String oldRequestStatus;
    public final String newRequestStatus;
  }
}
