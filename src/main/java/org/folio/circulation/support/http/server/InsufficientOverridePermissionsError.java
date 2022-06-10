package org.folio.circulation.support.http.server;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.http.server.ErrorCode.UNSEND_DEFAULT_VALUE;

import org.folio.circulation.domain.override.OverridableBlockType;
import org.folio.circulation.support.http.OkapiPermissions;

public class InsufficientOverridePermissionsError extends BlockOverrideError {

  public InsufficientOverridePermissionsError(OverridableBlockType blockType,
    OkapiPermissions missingOverridePermissions) {

    super("Insufficient override permissions", emptyMap(), UNSEND_DEFAULT_VALUE.toString(),
      blockType, missingOverridePermissions);
  }
}
