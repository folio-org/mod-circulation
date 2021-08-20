package api.support.builders;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@With
class CheckOutBlockOverrides extends JsonBuilder implements Builder {
  private final JsonObject itemNotLoanableBlockOverride;
  private final JsonObject patronBlockOverride;
  private final JsonObject itemLimitBlockOverride;
  private final String comment;

  @Override
  public JsonObject create() {
    JsonObject blockOverrides = new JsonObject();

    if (itemNotLoanableBlockOverride != null) {
      put(blockOverrides, "itemNotLoanableBlock", itemNotLoanableBlockOverride);
    }

    if (patronBlockOverride != null) {
      put(blockOverrides, "patronBlock", patronBlockOverride);
    }

    if (itemLimitBlockOverride != null) {
      put(blockOverrides, "itemLimitBlock", itemLimitBlockOverride);
    }

    put(blockOverrides, "comment", comment);

    return blockOverrides;
  }
}
