package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
public class CirculationSettingBuilder extends JsonBuilder implements Builder {
  private UUID id = null;
  private String name = null;
  private JsonObject value = null;

  @Override
  public JsonObject create() {
    JsonObject circulationSetting = new JsonObject();

    if (id != null) {
      put(circulationSetting, "id", id);
    }
    if (name != null) {
      put(circulationSetting, "name", name);
    }
    if (value != null) {
      put(circulationSetting, "value", value);
    }

    return circulationSetting;
  }
}
