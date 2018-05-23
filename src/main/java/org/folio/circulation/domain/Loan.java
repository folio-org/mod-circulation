package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.InventoryRecords;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private static final String STATUS_PROPERTY_NAME = "status";

  private final JsonObject representation;
  private InventoryRecords inventoryRecords;

  public Loan(JsonObject representation) {
    this.representation = representation;
  }

  public static Loan from(JsonObject representation) {
    return new Loan(representation);
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public void changeDueDate(DateTime dueDate) {
    representation.put("dueDate",
      dueDate.toString(ISODateTimeFormat.dateTime()));
  }

  public void changeUser(String userId) {
    representation.put("userId", userId);
  }

  public void changeItem(String itemId) {
    representation.put("itemId", itemId);
  }

  public void changeProxyUser(String userId) {
    representation.put("proxyUserId", userId);
  }

  public HttpResult<Void> isValidStatus() {
    if(!representation.containsKey(STATUS_PROPERTY_NAME)) {
      return HttpResult.failure(new ServerErrorFailure(
        "Loan does not have a status"));
    }

    switch(getStatus()) {
      case "Open":
      case "Closed":
        return HttpResult.success(null);

      default:
        return HttpResult.failure(new ValidationErrorFailure(
          "Loan status must be \"Open\" or \"Closed\"", STATUS_PROPERTY_NAME, getStatus()));
    }
  }

  boolean isClosed() {
    return StringUtils.equals(getStatus(), "Closed");
  }

  private String getStatus() {
    return getNestedStringProperty(representation, STATUS_PROPERTY_NAME, "name");
  }

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  public DateTime getLoanDate() {
    return DateTime.parse(representation.getString("loanDate"));
  }

  String getUserId() {
    return representation.getString("userId");
  }

  @Override
  public String getRequesterId() {
    return getUserId();
  }

  @Override
  public String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  public InventoryRecords getInventoryRecords() {
    return inventoryRecords;
  }

  void setInventoryRecords(InventoryRecords inventoryRecords) {
    this.inventoryRecords = inventoryRecords;
  }
}
