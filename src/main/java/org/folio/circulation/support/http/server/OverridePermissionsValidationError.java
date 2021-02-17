package org.folio.circulation.support.http.server;

import java.util.List;
import java.util.Map;

import org.folio.circulation.resources.handlers.error.OverridableBlockType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OverridePermissionsValidationError extends ValidationError {
  private final OverridableBlockType overridableBlockType;
  private final List<String> missingOverridePermissions;

  public OverridePermissionsValidationError(ValidationError validationError,
    OverridableBlockType circulationBlockType, List<String> missingOverridePermissions) {

    this(validationError.getMessage(), validationError.getParameters(),
      circulationBlockType, missingOverridePermissions);
  }

  public OverridePermissionsValidationError(String message, Map<String, String> parameters,
    OverridableBlockType circulationBlockType, List<String> missingOverridePermissions) {

    super(message, parameters);
    this.overridableBlockType = circulationBlockType;
    this.missingOverridePermissions = missingOverridePermissions;
  }

  @Override
  public JsonObject toJson() {
    return super.toJson()
      .put("overridableBlock", new JsonObject()
        .put("name", overridableBlockType.getName())
        .put("missingPermissions", new JsonArray(missingOverridePermissions)));
  }
}
