package org.folio.circulation.domain.override;

import static lombok.AccessLevel.PROTECTED;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = PROTECTED)
public abstract class BlockOverride {
  private final boolean requested;
}