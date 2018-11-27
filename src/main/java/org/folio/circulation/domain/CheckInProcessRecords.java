package org.folio.circulation.domain;

import java.util.Optional;

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
  private final RequestQueue requestQueue;

  public CheckInProcessRecords(CheckInByBarcodeRequest checkInRequest) {
    this(checkInRequest, null, null, null);
  }

  private CheckInProcessRecords(
    CheckInByBarcodeRequest checkInRequest,
    Item item,
    Loan loan,
    RequestQueue requestQueue) {

    this.checkInRequest = checkInRequest;
    this.item = item;
    this.loan = loan;
    this.requestQueue = requestQueue;
  }

  public CheckInProcessRecords withItem(Item item) {

    //When the item is updated, also update the item for the loan,
    //as they should be the same
    final Loan updatedLoan = Optional.ofNullable(loan)
      .map(l -> l.withItem(item))
      .orElse(null);

    return new CheckInProcessRecords(
      this.checkInRequest,
      item,
      updatedLoan,
      this.requestQueue);
  }

  public CheckInProcessRecords withLoan(Loan loan) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      loan, this.requestQueue);
  }

  public CheckInProcessRecords withRequestQueue(RequestQueue requestQueue) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      requestQueue);
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

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }
}
