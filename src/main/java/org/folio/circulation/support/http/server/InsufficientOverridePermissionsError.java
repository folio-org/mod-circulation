package org.folio.circulation.support.http.server;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.ErrorCode.INSUFFICIENT_OVERRIDE_PERMISSIONS;

import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;

public class InsufficientOverridePermissionsError extends BlockOverrideError {

  public InsufficientOverridePermissionsError(OverridableBlockType blockType,
    OkapiPermissions missingOverridePermissions) {

    super("Insufficient override permissions", emptyMap(), INSUFFICIENT_OVERRIDE_PERMISSIONS,
      blockType, missingOverridePermissions);
  }
}
