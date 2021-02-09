package org.folio.circulation.resources.handlers.error.override;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class BlockOverride {
  private final OverridableBlockType blockType;
}
