package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class ExpiredHoldsRequest {

  private static final String REQUESTER_NAME_KEY = "requesterName";
  private static final String REQUESTER_BARCODE_KEY = "requesterBarcode";
  private static final String ITEM_TITLE_KEY = "itemTitle";
  private static final String ITEM_BARCODE_KEY = "itemBarcode";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String REQUEST_STATUS_KEY = "requestStatus";
  private static final String HOLD_SHELF_EXPIRATION_DATE_KEY = "holdShelfExpirationDate";

  private static final String LAST_NAME_KEY = "lastName";
  private static final String FIRST_NAME_KEY = "firstName";
  private static final String BARCODE_KEY = "barcode";

  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern(DATE_TIME_FORMAT).withZoneUTC();

  private final JsonObject representation;

  public ExpiredHoldsRequest(Request request) {
    JsonObject requester = request.getRequesterFromRepresentation();
    Item item = Item.from(request.getItemFromRepresentation());

    this.representation = new JsonObject()
      .put(REQUESTER_NAME_KEY, getFullName(requester))
      .put(REQUESTER_BARCODE_KEY, getValue(getValueForJson(requester, BARCODE_KEY)))
      .put(ITEM_TITLE_KEY, getValue(item.getTitle()))
      .put(ITEM_BARCODE_KEY, getValue(item.getBarcode()))
      .put(CALL_NUMBER_KEY, getValue(item.getCallNumberFromItemRepresentation()))
      .put(REQUEST_STATUS_KEY, getValue(request.getStatus().getValue()))
      .put(HOLD_SHELF_EXPIRATION_DATE_KEY, DATE_TIME_FORMATTER.print(
        request.getHoldShelfExpirationDate()));
  }

  private String getFullName(JsonObject requester) {
    return String.join(", ",
      getValueForJson(requester, LAST_NAME_KEY),
      getValueForJson(requester, FIRST_NAME_KEY));
  }

  private String getValue(String val) {
    return StringUtils.isBlank(val)
      ? StringUtils.EMPTY
      : val;
  }

  private String getValueForJson(JsonObject requester, String key) {
    return requester.getString(key, StringUtils.EMPTY);
  }

  public JsonObject asJson() {
    return representation;
  }
}
