package org.folio.circulation.domain.override;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class BlockOverride {
  private final boolean requested;
}
