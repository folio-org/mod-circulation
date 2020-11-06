package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PACKAGE)
final class CancelAccountRequest {
  @Builder.Default
  private final boolean notifyPatron = true;
  private final String servicePointId;
  private final String userName;
  private final String cancellationReason;
  @Builder.Default
  private final String comments = "";

  JsonObject toJson() {
    final JsonObject json = new JsonObject();

    write(json, "notifyPatron", notifyPatron);
    write(json, "servicePointId", servicePointId);
    write(json, "userName", userName);
    write(json, "cancellationReason", cancellationReason);
    write(json, "comments", cancellationReason);

    return json;
  }
}
