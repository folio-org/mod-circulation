package org.folio.circulation.domain.override;

import org.folio.circulation.support.http.OkapiPermissions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OverridableBlockType {
  PATRON_BLOCK("patronBlock",
    OkapiPermissions.of("circulation.override-patron-block.post")),
  ITEM_LIMIT_BLOCK("itemLimitBlock",
    OkapiPermissions.of("circulation.override-item-limit-block.post")),
  ITEM_NOT_LOANABLE_BLOCK("itemNotLoanableBlock",
    OkapiPermissions.of("circulation.override-item-not-loanable-block.post")),
  RENEWAL_BLOCK("renewalBlock",
    OkapiPermissions.of("circulation.override-renewal-block.post")),
  RENEWAL_DUE_DATE_REQUIRED_BLOCK("renewalDueDateRequiredBlock",
    OkapiPermissions.of("circulation.override-renewal-block.post"));

  private final String name;
  private final OkapiPermissions requiredOverridePermissions;

  public OkapiPermissions getMissingOverridePermissions(OkapiPermissions existingPermissions) {
    return requiredOverridePermissions.getAllNotContainedIn(existingPermissions);
  }
}
