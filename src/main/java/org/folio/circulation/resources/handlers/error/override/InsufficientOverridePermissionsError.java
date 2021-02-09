package org.folio.circulation.resources.handlers.error.override;

import static java.util.Collections.emptyMap;

import java.util.List;

import org.folio.circulation.support.http.server.OverridableValidationError;

// TODO: think of a different name for parent, error of this class is not overridable
public class InsufficientOverridePermissionsError extends OverridableValidationError {

  public InsufficientOverridePermissionsError(OverridableBlockType blockType,
    List<String> missingOverridePermissions) {

    super("Insufficient override permissions", emptyMap(),
      blockType, missingOverridePermissions);
  }
}
