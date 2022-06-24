package org.folio.circulation.services;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostRecordRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostRecordRepository) {
    this.actualCostRecordRepository = actualCostRecordRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createIfNecessaryForDeclaredLostItem(
    ReferenceDataContext referenceDataContext) {

    Loan loan = referenceDataContext.getLoan();
    FeeFineOwner owner = referenceDataContext.getFeeFineOwner();
    ItemLossType itemLossType = ItemLossType.DECLARED_LOST;
    ZonedDateTime dateOfLoss = ClockUtil.getZonedDateTime();
    FeeFine feeFineType = referenceDataContext.getFeeFines().stream()
      .filter(feeFine -> LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals((feeFine.getFeeFineType())))
      .findFirst()
      .orElse(null);

    return createActualCostRecordIfNecessary(loan, owner, itemLossType, dateOfLoss, feeFineType)
      .thenApply(mapResult(referenceDataContext::withActualCostRecord));
  }

  public CompletableFuture<Result<LoanToChargeFees>> createIfNecessaryForAgedToLostItem(
    LoanToChargeFees loanToChargeFees) {

    Loan loan = loanToChargeFees.getLoan();
    FeeFineOwner owner = loanToChargeFees.getOwner();
    ItemLossType itemLossType = ItemLossType.AGED_TO_LOST;
    ZonedDateTime dateOfLoss = loan.getAgedToLostDateTime();
    FeeFine feeFineType = loanToChargeFees.getFeeFineTypes().get(LOST_ITEM_ACTUAL_COST_FEE_TYPE);

    return createActualCostRecordIfNecessary(loan, owner, itemLossType, dateOfLoss, feeFineType)
      .thenApply(mapResult(loanToChargeFees::withActualCostRecord));
  }

  private CompletableFuture<Result<ActualCostRecord>> createActualCostRecordIfNecessary(
    Loan loan, FeeFineOwner feeFineOwner, ItemLossType itemLossType,
    ZonedDateTime dateOfLoss, FeeFine feeFine) {

    return loan.getLostItemPolicy().hasActualCostFee()
      ? actualCostRecordRepository.createActualCostRecord(
        buildActualCostRecord(loan, feeFineOwner, itemLossType, dateOfLoss, feeFine))
      : completedFuture(succeeded(null));
  }

  private ActualCostRecord buildActualCostRecord(Loan loan, FeeFineOwner feeFineOwner,
    ItemLossType itemLossType, ZonedDateTime dateOfLoss, FeeFine feeFine) {

    Item item = loan.getItem();

    return new ActualCostRecord()
      .withUserId(loan.getUserId())
      .withUserBarcode(loan.getUser().getBarcode())
      .withLoanId(loan.getId())
      .withItemLossType(itemLossType)
      .withDateOfLoss(dateOfLoss.toString())
      .withTitle(item.getTitle())
      .withIdentifiers(item.getIdentifiers().collect(Collectors.toList()))
      .withItemBarcode(item.getBarcode())
      .withLoanType(item.getLoanTypeName())
      .withCallNumberComponents(item.getCallNumberComponents())
      .withPermanentItemLocation(item.getPermanentLocationName())
      .withFeeFineOwnerId(feeFineOwner.getId())
      .withFeeFineOwner(feeFineOwner.getOwner())
      .withFeeFineTypeId(feeFine == null ? null : feeFine.getId())
      .withFeeFineType(feeFine == null ? null : feeFine.getFeeFineType());
  }
}
