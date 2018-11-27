package org.folio.circulation.domain;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;

/**
 * The loan captures a snapshot of the item status
 * in order to populate the loan action history.
 *
 * This means that the check in process needs to remember
 * when a loan is closed, until after the item status is
 * updated.
 *
 * Which requires passing the records between processes.
 */
public class CheckInProcessRecords {
  private final CheckInByBarcodeRequest checkInRequest;
  private final Item item;
  private final Loan loan;

  public CheckInProcessRecords(CheckInByBarcodeRequest checkInRequest) {
    this(checkInRequest, null, null);
  }

  private CheckInProcessRecords(
    CheckInByBarcodeRequest checkInRequest,
    Item item, Loan loan) {

    this.checkInRequest = checkInRequest;
    this.item = item;
    this.loan = loan;
  }

  public CheckInProcessRecords withItem(Item item) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      item,
      this.loan);
  }

  public CheckInProcessRecords withLoan(Loan loan) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      loan);
  }

  public String getCheckInRequestBarcode() {
    return checkInRequest.getItemBarcode();
  }

  public Item getItem() {
    return item;
  }

  public Loan getLoan() {
    return loan;
  }

  public CheckInByBarcodeRequest getCheckInRequest() {
    return checkInRequest;
  }
}
