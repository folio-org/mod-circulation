package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.resources.handlers.error.OverridableBlockType.PATRON_BLOCK;

import java.util.List;

import org.folio.circulation.domain.representations.OverrideBlocks;

public class OverrideAutomatedPatronBlocksValidator extends LoanOverrideValidator {
  public OverrideAutomatedPatronBlocksValidator(OverrideBlocks overrideBlocks,
    List<String> permissions) {

    super(PATRON_BLOCK, overrideBlocks, permissions);
  }
}
