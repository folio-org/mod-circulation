package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class OverrideByBarcodeRequest extends RenewByBarcodeRequest {
  private static final String COMMENT_PROPERTY = "comment";
  private static final String DUE_DATE = "dueDate";

  private final String comment;
  private final DateTime dueDate;

  private OverrideByBarcodeRequest(
    String itemBarcode,
    String userBarcode,
    String comment,
    DateTime dueDate) {

    super(itemBarcode, userBarcode);

    this.comment = comment;
    this.dueDate = dueDate;
  }

  public String getComment() {
    return comment;
  }

  public DateTime getDueDate() {
    return dueDate;
  }

  static Result<OverrideByBarcodeRequest> renewalOverrideRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);
    if(StringUtils.isBlank(itemBarcode)) {
      return failedValidation("Override renewal request must have an item barcode",
        ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);
    if(StringUtils.isBlank(userBarcode)) {
      return failedValidation("Override renewal request must have a user barcode",
        USER_BARCODE, null);
    }

    final String comment = getProperty(json, COMMENT_PROPERTY);
    if(StringUtils.isBlank(comment)) {
      return failedValidation("Override renewal request must have a comment",
        COMMENT_PROPERTY, null);
    }
    final DateTime dueDate = getDateTimeProperty(json, DUE_DATE);

    return succeeded(new OverrideByBarcodeRequest(itemBarcode, userBarcode,
      comment, dueDate));
  }
}
