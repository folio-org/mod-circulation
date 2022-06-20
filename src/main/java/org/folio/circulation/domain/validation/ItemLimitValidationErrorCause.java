package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_LOAN_TYPE;
import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_MATERIAL_TYPE;
import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_MATERIAL_TYPE_LOAN_TYPE;
import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_PATRON_GROUP_LOAN_TYPE;
import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_PATRON_GROUP_MATERIAL_TYPE;
import static org.folio.circulation.support.ErrorCode.ITEM_LIMIT_PATRON_GROUP_MATERIAL_TYPE_LOAN_TYPE;

import org.folio.circulation.support.ErrorCode;

public enum ItemLimitValidationErrorCause {
  CAN_NOT_DETERMINE("can not determine item limit validation error cause", null),
  PATRON_GROUP_MATERIAL_TYPE_LOAN_TYPE("for combination of patron group, material type and loan type",
    ITEM_LIMIT_PATRON_GROUP_MATERIAL_TYPE_LOAN_TYPE),
  PATRON_GROUP_MATERIAL_TYPE("for combination of patron group and material type",
    ITEM_LIMIT_PATRON_GROUP_MATERIAL_TYPE),
  PATRON_GROUP_LOAN_TYPE("for combination of patron group and loan type",
    ITEM_LIMIT_PATRON_GROUP_LOAN_TYPE),
  MATERIAL_TYPE_AND_LOAN_TYPE("for combination of material type and loan type",
    ITEM_LIMIT_MATERIAL_TYPE_LOAN_TYPE),
  MATERIAL_TYPE("for material type", ITEM_LIMIT_MATERIAL_TYPE),
  LOAN_TYPE("for loan type", ITEM_LIMIT_LOAN_TYPE);

  private Integer itemLimit;
  private String description;
  private ErrorCode errorCode;

  ItemLimitValidationErrorCause(String description, ErrorCode errorCode) {
    this.description = description;
    this.errorCode = errorCode;
  }

  public String formatMessage() {
    return String.format("Patron has reached maximum limit of %d items %s", itemLimit, description);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public void setItemLimit(Integer itemLimit) {
    this.itemLimit = itemLimit;
  }
}
