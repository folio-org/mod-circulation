package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedResult;

public class OverrideByBarcodeRequest {

  public static final String USER_BARCODE = "userBarcode";
  public static final String ITEM_BARCODE = "itemBarcode";
  public static final String COMMENT_PROPERTY = "comment";
  public static final String DUE_DATE = "dueDate";

  private final String itemBarcode;
  private final String userBarcode;
  private final String comment;
  private final DateTime dueDate;

  public OverrideByBarcodeRequest(String itemBarcode, String userBarcode, String comment, DateTime dueDate) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.comment = comment;
    this.dueDate = dueDate;
  }

  public String getItemBarcode() {
    return itemBarcode;
  }

  public String getUserBarcode() {
    return userBarcode;
  }

  public String getComment() {
    return comment;
  }

  public DateTime getDueDate() {
    return dueDate;
  }

  public static HttpResult<OverrideByBarcodeRequest> from(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);
    if(StringUtils.isBlank(itemBarcode)) {
      return failedResult("Override renewal request must have an item barcode", ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);
    if(StringUtils.isBlank(userBarcode)) {
      return failedResult("Override renewal request must have a user barcode", USER_BARCODE, null);
    }

    final String comment = getProperty(json, COMMENT_PROPERTY);
    if(StringUtils.isBlank(comment)) {
      return failedResult("Override renewal request must have a comment", COMMENT_PROPERTY, null);
    }
    final DateTime dueDate = getDateTimeProperty(json, DUE_DATE);

    return succeeded(new OverrideByBarcodeRequest(itemBarcode, userBarcode, comment, dueDate));
  }

}
