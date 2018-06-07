package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.FindByBarcodeQuery;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import static org.folio.circulation.support.HttpResult.success;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class RenewByBarcodeRequest implements FindByBarcodeQuery {
  private static final String USER_BARCODE = "userBarcode";
  private static final String ITEM_BARCODE = "itemBarcode";

  private final String itemBarcode;
  private final String userBarcode;

  private RenewByBarcodeRequest(String itemBarcode, String userBarcode) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
  }

  public static HttpResult<RenewByBarcodeRequest> from(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if(StringUtils.isBlank(itemBarcode)) {
      return failure("Renewal request must have an item barcode", ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if(StringUtils.isBlank(userBarcode)) {
      return failure("Renewal request must have a user barcode", USER_BARCODE, null);
    }

    return success(new RenewByBarcodeRequest(itemBarcode, userBarcode));
  }

  @Override
  public String getItemBarcode() {
    return itemBarcode;
  }

  @Override
  public String getUserBarcode() {
    return userBarcode;
  }

  @Override
  public ValidationErrorFailure userDoesNotMatchError() {
    return ValidationErrorFailure.error("Cannot renew item checked out to different user",
      USER_BARCODE, getUserBarcode());
  }

  @Override
  public boolean userMatches(User user) {
    return StringUtils.equals(user.getBarcode(), getUserBarcode());
  }
}
