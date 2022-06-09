package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import org.folio.circulation.domain.FeeAmount;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PACKAGE)
final class RefundAccountRequest {
  private final FeeAmount amount;
  private final String servicePointId;
  private final String userName;
  private final String refundReason;

  JsonObject toJson() {
    final JsonObject json = new JsonObject();

    write(json, "amount", amount.toScaledString());
    write(json, "notifyPatron", true);
    write(json, "servicePointId", servicePointId);
    write(json, "userName", userName);
    write(json, "paymentMethod", refundReason);

    return json;
  }
}
