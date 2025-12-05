package org.folio.circulation.domain.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum CirculationSettingName {
  TLR("TLR"), // unified TLR feature settings migrated from single-tenant mod-configuration
  REGULAR_TLR("regularTlr"), // TLR notice templates settings migrated from multi-tenant mod-settings
  GENERAL_TLR("generalTlr"), // TLR settings migrated from multi-tenant mod-settings
  PRINT_HOLD_REQUESTS("PRINT_HOLD_REQUESTS"),
  NOTICES_LIMIT("noticesLimit"),
  OTHER_SETTINGS("other_settings"),
  LOAN_HISTORY("loan_history");

  private final String value;
}
