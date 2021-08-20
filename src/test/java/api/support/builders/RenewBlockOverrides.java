package api.support.builders;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@Getter
@With
class RenewBlockOverrides extends JsonBuilder implements Builder {
  private final JsonObject itemNotLoanableBlock;
  private final JsonObject patronBlock;
  private final JsonObject itemLimitBlock;
  private final JsonObject renewalBlock;
  private final JsonObject renewalDueDateRequiredBlock;
  private final String comment;

  @Override
  public JsonObject create() {
    JsonObject blockOverrides = new JsonObject();

    if (itemNotLoanableBlock != null) {
      put(blockOverrides, "itemNotLoanableBlock", itemNotLoanableBlock);
    }

    if (patronBlock != null) {
      put(blockOverrides, "patronBlock", patronBlock);
    }

    if (itemLimitBlock != null) {
      put(blockOverrides, "itemLimitBlock", itemLimitBlock);
    }

    if (renewalBlock != null) {
      put(blockOverrides, "renewalBlock", renewalBlock);
    }

    if (renewalDueDateRequiredBlock != null) {
      put(blockOverrides, "renewalDueDateRequiredBlock", renewalDueDateRequiredBlock);
    }

    put(blockOverrides, "comment", comment);

    return blockOverrides;
  }
}
