package org.folio.circulation.services.agedtolost;

import static java.util.function.Function.identity;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class LoanToChargeFees {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Loan loan;
  private final FeeFineOwner owner;
  private final Map<String, FeeFine> feeFineTypes;
  private final ActualCostRecord actualCostRecord;

  boolean hasNoLostItemFee() {
    return getLostItemFeeType() == null;
  }

  boolean hasNoLostItemProcessingFee() {
    return getLostItemProcessingFeeType() == null;
  }

  boolean hasNoFeeFineOwner() {
    return owner == null;
  }

  public String getPrimaryServicePointId() {
    return Optional.of(loan)
      .map(Loan::getItem)
      .map(Item::getPermanentLocation)
      .map(Location::getPrimaryServicePointId)
      .map(UUID::toString)
      .orElseGet(() -> {
        log.error("Failed to get servicePointId for loan {}", loan::getId);
        return null;
      });
  }

  FeeFine getLostItemFeeType() {
    return feeFineTypes.get(LOST_ITEM_FEE_TYPE);
  }

  FeeFine getLostItemProcessingFeeType() {
    return feeFineTypes.get(LOST_ITEM_PROCESSING_FEE_TYPE);
  }

  LostItemPolicy getLostItemPolicy() {
    return loan.getLostItemPolicy();
  }

  String getLoanId() {
    return loan != null ? loan.getId() : null;
  }

  LoanToChargeFees withFeeFineTypes(Collection<FeeFine> allFeeFines) {
    log.debug("withFeeFineTypes:: parameters feeFines: {}", () -> collectionAsString(allFeeFines));
    final Map<String, FeeFine> feeFineTypeToFeeFineMap = allFeeFines.stream()
      .collect(Collectors.toMap(FeeFine::getFeeFineType, identity()));

    return new LoanToChargeFees(loan, owner, feeFineTypeToFeeFineMap, actualCostRecord);
  }

  public LoanToChargeFees withActualCostRecord(ActualCostRecord actualCostRecord) {
    return new LoanToChargeFees(loan, owner, feeFineTypes, actualCostRecord);
  }

  LoanToChargeFees withOwner(Map<String, FeeFineOwner> owners) {
    return new LoanToChargeFees(loan, owners.get(getPrimaryServicePointId()), feeFineTypes,
      actualCostRecord);
  }

  boolean shouldCloseLoan() {
    return getLostItemPolicy().hasNoLostItemFee()
      && !getLostItemPolicy().getAgeToLostProcessingFee().isChargeable();
  }

  public static LoanToChargeFees usingLoan(Loan loan) {
    return new LoanToChargeFees(loan, null, Collections.emptyMap(), null);
  }

  static List<LoanToChargeFees> usingLoans(MultipleRecords<Loan> loans) {
    return loans.getRecords().stream()
      .map(LoanToChargeFees::usingLoan)
      .collect(Collectors.toList());
  }
}
