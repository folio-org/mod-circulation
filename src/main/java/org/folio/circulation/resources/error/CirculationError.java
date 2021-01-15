package org.folio.circulation.resources.error;

import static org.folio.circulation.resources.error.OverridableBlock.ITEM_LIMIT;
import static org.folio.circulation.resources.error.OverridableBlock.ITEM_NOT_LOANABLE;
import static org.folio.circulation.resources.error.OverridableBlock.PATRON_BLOCK;

public enum CirculationError {
  INVALID_ITEM,
  INVALID_ITEM_ID,
  INVALID_USER_OR_PATRON_GROUP,
  INVALID_STATUS,
  INVALID_PROXY_RELATIONSHIP,
  INVALID_PICKUP_SERVICE_POINT,

  FAILED_TO_FETCH_ITEM,
  FAILED_TO_FETCH_USER,
  FAILED_TO_FETCH_PROXY_USER,
  FAILED_TO_FETCH_SERVICE_POINT,
  FAILED_TO_FETCH_LOAN,
  FAILED_TO_FETCH_USER_FOR_LOAN,
  FAILED_TO_FETCH_REQUEST_QUEUE,
  FAILED_TO_FETCH_REQUEST_POLICY,
  FAILED_TO_FETCH_TIME_ZONE_CONFIG,
  FAILED_TO_FETCH_LOAN_POLICY,

  ITEM_ALREADY_REQUESTED_BY_SAME_USER,
  ITEM_REQUESTED_BY_ANOTHER_PATRON,
  ITEM_ALREADY_LOANED_TO_SAME_USER,
  ITEM_IS_NOT_LOANABLE(ITEM_NOT_LOANABLE),
  ITEM_IS_NOT_ALLOWED_FOR_CHECK_OUT,
  ITEM_ALREADY_CHECKED_OUT,
  ITEM_HAS_OPEN_LOANS,
  ITEM_LIMIT_IS_REACHED(ITEM_LIMIT),
  USER_IS_INACTIVE,
  PROXY_USER_IS_INACTIVE,
  PROXY_USER_EQUALS_TO_USER,
  USER_IS_BLOCKED_MANUALLY,
  USER_IS_BLOCKED_AUTOMATICALLY(PATRON_BLOCK),
  REQUESTING_DISALLOWED_BY_REQUEST_POLICY,
  REQUESTING_DISALLOWED_FOR_ITEM,
  SERVICE_POINT_IS_NOT_PRESENT,;

  OverridableBlock overridableBlock;

  CirculationError() {
    this(null);
  }

  CirculationError(OverridableBlock overridableBlock) {
    this.overridableBlock = overridableBlock;
  }

  public OverridableBlock getOverridableBlock() {
    return overridableBlock;
  }

  public boolean isOverridable() {
    return overridableBlock != null;
  }
}
