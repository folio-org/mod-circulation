package org.folio.circulation.resources.handlers.error;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OverridableBlockType {
  PATRON_BLOCK("patronBlock",
    List.of("circulation.override-patron-block")),
  ITEM_LIMIT_BLOCK("itemLimitBlock",
    List.of("circulation.override-item-limit-block")),
  ITEM_NOT_LOANABLE_BLOCK("itemNotLoanableBlock",
    List.of("circulation.override-item-not-loanable-block"));

  private final String name;
  private final List<String> requiredOverridePermissions;

  public List<String> getMissingOverridePermissions(Collection<String> existingPermissions) {
    return requiredOverridePermissions.stream()
      .filter(not(existingPermissions::contains))
      .collect(toList());
  }
}
