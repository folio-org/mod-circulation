package org.folio.circulation.support.http.server.error;

import static java.util.Collections.emptyMap;

import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;

public class InsufficientOverridePermissionsError extends BlockOverrideError {

  public InsufficientOverridePermissionsError(OverridableBlockType blockType,
    OkapiPermissions missingOverridePermissions) {

    super("Insufficient override permissions", emptyMap(), null,
      blockType, missingOverridePermissions);
  }
}
