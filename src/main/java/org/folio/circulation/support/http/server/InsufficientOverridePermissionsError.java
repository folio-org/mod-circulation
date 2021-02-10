package org.folio.circulation.support.http.server;

import static java.util.Collections.emptyMap;

import java.util.List;

import org.folio.circulation.resources.handlers.error.OverridableBlockType;

public class InsufficientOverridePermissionsError extends OverridableValidationError {

  public InsufficientOverridePermissionsError(OverridableBlockType blockType,
    List<String> missingOverridePermissions) {

    super("Insufficient override permissions", emptyMap(),
      blockType, missingOverridePermissions);
  }
}
