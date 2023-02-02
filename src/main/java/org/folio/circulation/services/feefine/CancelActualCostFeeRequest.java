package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class CancelActualCostFeeRequest {
  private final String actualCostRecordId;
  private final String additionalInfoForStaff;
  @ToString.Exclude
  private final String additionalInfoForPatron;

  JsonObject toJson() {
    final JsonObject json = new JsonObject();
    write(json, "actualCostRecordId", actualCostRecordId);
    write(json, "additionalInfoForPatron", additionalInfoForPatron);
    write(json, "additionalInfoForStaff", additionalInfoForStaff);

    return json;
  }
}
