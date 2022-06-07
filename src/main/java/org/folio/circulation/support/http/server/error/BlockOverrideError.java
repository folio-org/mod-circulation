package org.folio.circulation.support.http.server.error;

import java.util.ArrayList;
import java.util.Map;

import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class BlockOverrideError extends ValidationError {
  private final OverridableBlockType blockType;
  private final OkapiPermissions missingOverridePermissions;

  public BlockOverrideError(ValidationError validationError,
    OverridableBlockType blockType, OkapiPermissions missingOverridePermissions) {

    this(validationError.getMessage(), validationError.getParameters(), validationError.getCode(),
      blockType, missingOverridePermissions);
  }

  public BlockOverrideError(String message, Map<String, String> parameters, String code,
    OverridableBlockType blockType, OkapiPermissions missingOverridePermissions) {

    super(message, parameters, code);
    this.blockType = blockType;
    this.missingOverridePermissions = missingOverridePermissions;
  }

  @Override
  public JsonObject toJson() {
    return super.toJson()
      .put("overridableBlock", new JsonObject()
        .put("name", blockType.getName())
        .put("missingPermissions", new JsonArray(
          new ArrayList<>(missingOverridePermissions.getPermissions()))));
  }
}
