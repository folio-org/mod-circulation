package org.folio.circulation.domain;

public class AccountLoanAndItemInfo {
  private final String title;
  private final String barcode;
  private final String callNumber;
  private final String location;
  private final String materialTypeId;
  private final String loanId;
  private final String userId;
  private final String itemId;

  public AccountLoanAndItemInfo(String title, String barcode, String callNumber, String location,
    String materialTypeId, String loanId, String userId, String itemId) {
    this.title = title;
    this.barcode = barcode;
    this.callNumber = callNumber;
    this.location = location;
    this.materialTypeId = materialTypeId;
    this.loanId = loanId;
    this.userId = userId;
    this.itemId = itemId;
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

  public String getLoanId() {
    return loanId;
  }

  public String getUserId() {
    return userId;
  }

  public String getItemId() {
    return itemId;
  }
}
