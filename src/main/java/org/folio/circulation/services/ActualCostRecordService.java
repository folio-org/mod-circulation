package org.folio.circulation.services;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.infrastructure.storage.ActualCostStorageRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
public class ActualCostRecordService {
  private final ActualCostStorageRepository actualCostStorageRepository;

  public ActualCostRecordService(ActualCostStorageRepository actualCostStorageRepository) {
    this.actualCostStorageRepository = actualCostStorageRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createActualCostRecordIfNecessary(
    ReferenceDataContext referenceDataContext) {
    return createActualCostRecordIfNecessary(referenceDataContext.getLoan(),
      referenceDataContext.getFeeFineOwner(), ItemLossType.DECLARED_LOST, ZonedDateTime.now())
      .thenApply(mapResult(referenceDataContext::withActualCostRecord));
  }

  public CompletableFuture<Result<LoanToChargeFees>> createActualCostRecordIfNecessary(
    LoanToChargeFees loanToChargeFees) {

    Loan loan = loanToChargeFees.getLoan();
    return createActualCostRecordIfNecessary(loan, loanToChargeFees.getOwner(), ItemLossType.AGED_TO_LOST,
      loan.getAgedToLostDateTime())
      .thenApply(mapResult(loanToChargeFees::withActualCostRecord));
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecordIfNecessary(Loan loan,
    FeeFineOwner feeFineOwner, ItemLossType lossType, ZonedDateTime dateOfLoss) {

    return loan.getLostItemPolicy().hasActualCostFee() ?
      actualCostStorageRepository.createActualCostRecord(buildActualCostRecord(loan, feeFineOwner,
        lossType, dateOfLoss)) : completedFuture(succeeded(null));
  }

  private ActualCostRecord buildActualCostRecord(Loan loan, FeeFineOwner feeFineOwner,
    ItemLossType lossType, ZonedDateTime dateOfLoss) {

    Item item = loan.getItem();

    return new ActualCostRecord()
      .withUserId(loan.getUserId())
      .withUserBarcode(loan.getUser().getBarcode())
      .withLoanId(loan.getId())
      .withLossType(lossType)
      .withDateOfLoss(dateOfLoss.toString())
      .withTitle(item.getTitle())
      .withIdentifiers(item.getIdentifiers()
        .collect(Collectors.toList()))
      .withItemBarcode(item.getBarcode())
      .withLoanType(item.getLoanTypeName())
      .withEffectiveCallNumber(item.getCallNumber())
      .withPermanentItemLocation(item.getPermanentLocation().getName())
      .withFeeFineOwnerId(feeFineOwner.getId())
      .withFeeFineOwner(feeFineOwner.getOwner())
      .withFeeFineTypeId(LOST_ITEM_ACTUAL_COST_FEE_TYPE)
      .withFeeFineType(LOST_ITEM_ACTUAL_COST_FEE_TYPE);
  }
}
