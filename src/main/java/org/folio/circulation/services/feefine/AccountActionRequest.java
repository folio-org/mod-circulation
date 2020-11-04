package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import org.folio.circulation.domain.FeeAmount;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PACKAGE)
public final class AccountActionRequest {
  private final FeeAmount amount;
  @Builder.Default
  private final boolean notifyPatron = true;
  private final String servicePointId;
  private final String userName;
  private final String reason;

  JsonObject toJson() {
    final JsonObject json = new JsonObject();

    write(json, "amount", amount.toDouble());
    write(json, "notifyPatron", notifyPatron);
    write(json, "servicePointId", servicePointId);
    write(json, "userName", userName);
    write(json, "paymentMethod", reason);

    return json;
  }
}
