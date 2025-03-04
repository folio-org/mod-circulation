package org.folio.circulation.domain;

public class AccountItemInfo {
  private final String itemId;
  private final String title;
  private final String barcode;
  private final String callNumber;
  private final String location;
  private final String materialTypeId;
  private final String materialType;

  public AccountItemInfo(String itemId, String title, String barcode, String callNumber, String location, String materialTypeId, String materialType) {
    this.itemId = itemId;
    this.title = title;
    this.barcode = barcode;
    this.callNumber = callNumber;
    this.location = location;
    this.materialTypeId = materialTypeId;
    this.materialType = materialType;
  }

  public String getItemId() {
    return itemId;
  }

  public String getTitle() {
    return title;
  }

  public String getBarcode() {
    return barcode;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public String getLocation() {
    return location;
  }

  public String getMaterialTypeId() {
    return materialTypeId;
  }

  public String getMaterialType() {
    return materialType;
  }
}
