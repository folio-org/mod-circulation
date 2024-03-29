package api.support.builders;


import java.time.ZonedDateTime;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@With
public class RenewalDueDateRequiredBlockOverrideBuilder extends JsonBuilder implements Builder {
  private final ZonedDateTime dueDate;

  @Override
  public JsonObject create() {
    JsonObject blockOverrides = new JsonObject();

    put(blockOverrides, "dueDate", dueDate);

    return blockOverrides;
  }
}
