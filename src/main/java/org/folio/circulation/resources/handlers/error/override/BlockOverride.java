package org.folio.circulation.resources.handlers.error.override;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor()
public abstract class BlockOverride {
  @NonNull
  private final OverridableBlockType blockType;
}
