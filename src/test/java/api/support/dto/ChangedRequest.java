package api.support.dto;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class ChangedRequest {
  public final String id;
  public final String requestType;
  public final String oldRequestStatus;
  public final String newRequestStatus;

  public static ChangedRequest fromJson(JsonObject request) {
    return builder()
      .id(getProperty(request, "id"))
      .requestType(getProperty(request, "requestType"))
      .oldRequestStatus(getProperty(request, "oldRequestStatus"))
      .newRequestStatus(getProperty(request, "newRequestStatus"))
      .build();
  }
}
