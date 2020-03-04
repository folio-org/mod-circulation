package org.folio.circulation.domain;

import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.ClockManager;
import org.joda.time.DateTime;

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
public class CheckInProcessRecords implements LoanRelatedRecord {
  private final CheckInByBarcodeRequest checkInRequest;
  private final Item item;
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final ServicePoint checkInServicePoint;
  private final Request highestPriorityFulfillableRequest;
  private final String loggedInUserId;
  private final DateTime checkInProcessedDateTime;
  private final boolean inHouseUse;

  public CheckInProcessRecords(CheckInByBarcodeRequest checkInRequest) {
    this(checkInRequest, null, null, null, null, null, null, false);
  }

  private CheckInProcessRecords(
    CheckInByBarcodeRequest checkInRequest,
    Item item,
    Loan loan,
    RequestQueue requestQueue,
    ServicePoint checkInServicePoint,
    Request highestPriorityFulfillableRequest,
    String loggedInUserId,
    boolean inHouseUse) {
    this.checkInRequest = checkInRequest;
    this.item = item;
    this.loan = loan;
    this.requestQueue = requestQueue;
    this.checkInServicePoint = checkInServicePoint;
    this.highestPriorityFulfillableRequest = highestPriorityFulfillableRequest;
    this.loggedInUserId = loggedInUserId;
    checkInProcessedDateTime = ClockManager.getClockManager().getDateTime();
    this.inHouseUse = inHouseUse;
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
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withLoan(Loan loan) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      loggedInUserId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withRequestQueue(RequestQueue requestQueue) {
    Request firstRequest = null;
    if (requestQueue.hasOutstandingFulfillableRequests()) {
      firstRequest = requestQueue.getHighestPriorityFulfillableRequest();
    }
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      requestQueue,
      this.checkInServicePoint,
      firstRequest,
      loggedInUserId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withCheckInServicePoint(ServicePoint checkInServicePoint) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      this.requestQueue,
      checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      loggedInUserId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withHighestPriorityFulfillableRequest(Request request) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      request,
      loggedInUserId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withLoggedInUserId(String userId) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      userId,
      this.inHouseUse);
  }

  public CheckInProcessRecords withInHouseUse(boolean inHouseUse) {
    return new CheckInProcessRecords(
      this.checkInRequest,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      inHouseUse);
  }

  public boolean isInHouseUse() {
    return inHouseUse;
  }

  public String getCheckInRequestBarcode() {
    return checkInRequest.getItemBarcode();
  }

  public UUID getCheckInServicePointId() {
    return checkInRequest.getServicePointId();
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

  public ServicePoint getCheckInServicePoint() {
    return checkInServicePoint;
  }

  public Request getHighestPriorityFulfillableRequest() {
    return highestPriorityFulfillableRequest;
  }

  public String getLoggedInUserId() {
    return loggedInUserId;
  }

  public DateTime getCheckInProcessedDateTime() {
    return checkInProcessedDateTime;
  }
}
