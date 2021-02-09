package org.folio.circulation.support.http.server;

import java.util.List;
import java.util.Map;

import org.folio.circulation.resources.handlers.error.override.OverridableBlockType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class OverridableValidationError extends ValidationError {
  private final OverridableBlockType blockType;
  private final List<String> missingOverridePermissions;

  public OverridableValidationError(ValidationError validationError,
    OverridableBlockType blockType, List<String> missingOverridePermissions) {

    this(validationError.getMessage(), validationError.getParameters(),
      blockType, missingOverridePermissions);
  }

  public OverridableValidationError(String message, Map<String, String> parameters,
    OverridableBlockType blockType, List<String> missingOverridePermissions) {

    super(message, parameters);
    this.blockType = blockType;
    this.missingOverridePermissions = missingOverridePermissions;
  }

  @Override
  public JsonObject toJson() {
    return super.toJson()
      .put("overridableBlock", new JsonObject()
        .put("name", blockType.getName())
        .put("missingPermissions", new JsonArray(missingOverridePermissions)));
  }
}
