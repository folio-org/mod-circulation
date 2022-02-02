package org.folio.circulation.domain;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.utils.ClockUtil;

import lombok.AllArgsConstructor;

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
@AllArgsConstructor
public class CheckInContext {
  private final CheckInByBarcodeRequest checkInRequest;
  private final TlrSettingsConfiguration tlrSettings;
  private final Item item;
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final ServicePoint checkInServicePoint;
  private final Request highestPriorityFulfillableRequest;
  private final String loggedInUserId;
  private final ZonedDateTime checkInProcessedDateTime;
  private final boolean inHouseUse;
  private final ItemStatus itemStatusBeforeCheckIn;

  public CheckInContext(CheckInByBarcodeRequest checkInRequest) {
    this(checkInRequest, null, null, null, null, null, null, null,
      ClockUtil.getZonedDateTime(), false, null);
  }

  public CheckInContext withTlrSettings(TlrSettingsConfiguration tlrSettingsConfiguration) {
    return new CheckInContext(
      this.checkInRequest,
      tlrSettingsConfiguration,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withItem(Item item) {

    //When the item is updated, also update the item for the loan,
    //as they should be the same
    final Loan updatedLoan = Optional.ofNullable(loan)
      .map(l -> l.withItem(item))
      .orElse(null);

    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      item,
      updatedLoan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withLoan(Loan loan) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withRequestQueue(RequestQueue requestQueue) {
    Request firstRequest = null;

    if (requestQueue.hasOutstandingRequestsFulfillableByItem(item)) {
      firstRequest = requestQueue.getHighestPriorityRequestFulfillableByItem(item);
    }

    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      requestQueue,
      this.checkInServicePoint,
      firstRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withCheckInServicePoint(ServicePoint checkInServicePoint) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      this.requestQueue,
      checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withHighestPriorityFulfillableRequest(Request request) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      request,
      loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withLoggedInUserId(String userId) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      userId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withInHouseUse(boolean inHouseUse) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      inHouseUse,
      this.itemStatusBeforeCheckIn);
  }

  public CheckInContext withItemStatusBeforeCheckIn(ItemStatus itemStatus) {
    return new CheckInContext(
      this.checkInRequest,
      this.tlrSettings,
      this.item,
      this.loan,
      this.requestQueue,
      this.checkInServicePoint,
      this.highestPriorityFulfillableRequest,
      this.loggedInUserId,
      this.checkInProcessedDateTime,
      this.inHouseUse,
      itemStatus);
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

  public TlrSettingsConfiguration getTlrSettings() {
    return tlrSettings;
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

  public ZonedDateTime getCheckInProcessedDateTime() {
    return checkInProcessedDateTime;
  }

  public ItemStatus getItemStatusBeforeCheckIn() {
    return itemStatusBeforeCheckIn;
  }
}
