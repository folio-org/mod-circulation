package org.folio.circulation.services.agedtolost;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter(AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class LoanToChargeFees {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Loan loan;
  private final FeeFineOwner owner;
  private final Map<String, FeeFine> feeFineTypes;

  boolean hasNoLostItemFee() {
    return getLostItemFeeType() == null;
  }

  boolean hasNoLostItemProcessingFee() {
    return getLostItemProcessingFeeType() == null;
  }

  boolean hasNoFeeFineOwner() {
    return owner == null;
  }

  String getOwnerServicePointId() {
    try {
      return loan.getItem().getPermanentLocation().getPrimaryServicePointId().toString();
    } catch (RuntimeException e) {
      log.error(format("Failed to get service pointId for the loanId: \"%s\"", loan.getId()));
      return null;
    }
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

  LoanToChargeFees withFeeFineTypes(Collection<FeeFine> allFeeFines) {
    final Map<String, FeeFine> feeFineTypeToFeeFineMap = allFeeFines.stream()
      .collect(Collectors.toMap(FeeFine::getFeeFineType, identity()));

    return new LoanToChargeFees(loan, owner, feeFineTypeToFeeFineMap);
  }

  LoanToChargeFees withOwner(Map<String, FeeFineOwner> owners) {
    return new LoanToChargeFees(loan, owners.get(getOwnerServicePointId()), feeFineTypes);
  }

  boolean shouldCloseLoan() {
    return getLostItemPolicy().hasNoLostItemFee()
      && !getLostItemPolicy().getAgeToLostProcessingFee().isChargeable();
  }

  static LoanToChargeFees usingLoan(Loan loan) {
    return new LoanToChargeFees(loan, null, Collections.emptyMap());
  }

  static List<LoanToChargeFees> usingLoans(MultipleRecords<Loan> loans) {
    return loans.getRecords().stream()
      .map(LoanToChargeFees::usingLoan)
      .collect(Collectors.toList());
  }
}
