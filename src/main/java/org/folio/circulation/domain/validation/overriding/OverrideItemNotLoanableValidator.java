package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.resources.handlers.error.OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK;

import java.util.List;

import org.folio.circulation.domain.representations.OverrideBlocks;

public class OverrideItemNotLoanableValidator extends LoanOverrideValidator {
  public OverrideItemNotLoanableValidator(OverrideBlocks overrideBlocks,
    List<String> permissions) {

    super(ITEM_NOT_LOANABLE_BLOCK, overrideBlocks, permissions);
  }
}
