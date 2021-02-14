package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.resources.handlers.error.OverridableBlockType.ITEM_LIMIT_BLOCK;

import java.util.List;

import org.folio.circulation.domain.representations.OverrideBlocks;

public class OverrideItemLimitValidator extends LoanOverrideValidator {
  public OverrideItemLimitValidator(OverrideBlocks overrideBlocks, List<String> permissions) {
    super(ITEM_LIMIT_BLOCK, overrideBlocks, permissions);
  }
}
