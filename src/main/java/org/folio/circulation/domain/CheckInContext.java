package org.folio.circulation.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.utils.ClockUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

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
@With
@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class CheckInContext {
  @ToString.Include
  private final CheckInByBarcodeRequest checkInRequest;
  private final TlrSettingsConfiguration tlrSettings;
  private final Item item;
  @ToString.Include
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final ServicePoint checkInServicePoint;
  private final Request highestPriorityFulfillableRequest;
  private final String loggedInUserId;
  private final ZonedDateTime checkInProcessedDateTime;
  private final boolean inHouseUse;
  private final ItemStatus itemStatusBeforeCheckIn;
  private final ZoneId timeZone;

  public CheckInContext(CheckInByBarcodeRequest checkInRequest) {
    this(checkInRequest, null, null, null, null, null, null, null,
      ClockUtil.getZonedDateTime(), false, null, null);
  }

  /**
   * Updates the item in the check-in context.
   * Also updates the item for the loan, as they should be the same.
   * @param item the item to put into the check-in context
   * @return new CheckInContext with an updated item and loan
   */
  public CheckInContext withItemAndUpdatedLoan(Item item) {
    final Loan updatedLoan = Optional.ofNullable(loan)
      .map(l -> l.withItem(item))
      .orElse(null);

    return withItem(item)
      .withLoan(updatedLoan);
  }

  public String getCheckInRequestBarcode() {
    return checkInRequest.getItemBarcode();
  }

  public UUID getCheckInServicePointId() {
    return checkInRequest.getServicePointId();
  }

  public UUID getSessionId() {
    return checkInRequest.getSessionId();
  }

}
