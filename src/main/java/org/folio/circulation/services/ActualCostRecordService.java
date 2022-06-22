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
import org.folio.circulation.domain.Location;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostStorageRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostStorageRepository) {
    this.actualCostStorageRepository = actualCostStorageRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createActualCostRecordIfNecessary(
    ReferenceDataContext referenceDataContext) {
    return createActualCostRecordIfNecessary(referenceDataContext.getLoan(),
      referenceDataContext.getFeeFineOwner(), ItemLossType.DECLARED_LOST, ZonedDateTime.now(), referenceDataContext.getFeeFines().stream()
        .filter(feeFine -> LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(feeFine.getFeeFineType()))
        .findFirst().orElse(null))
      .thenApply(mapResult(referenceDataContext::withActualCostRecord));
  }

  public CompletableFuture<Result<LoanToChargeFees>> createActualCostRecordIfNecessary(
    LoanToChargeFees loanToChargeFees) {

    Loan loan = loanToChargeFees.getLoan();
    return createActualCostRecordIfNecessary(loan, loanToChargeFees.getOwner(), ItemLossType.AGED_TO_LOST,
      loan.getAgedToLostDateTime(), loanToChargeFees.getFeeFineTypes().get(LOST_ITEM_ACTUAL_COST_FEE_TYPE))
      .thenApply(mapResult(loanToChargeFees::withActualCostRecord));
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecordIfNecessary(Loan loan,
    FeeFineOwner feeFineOwner, ItemLossType lossType, ZonedDateTime dateOfLoss, FeeFine actualCostFeeFine) {

    return loan.getLostItemPolicy().hasActualCostFee() ?
      actualCostStorageRepository.createActualCostRecord(buildActualCostRecord(loan, feeFineOwner,
        lossType, dateOfLoss, actualCostFeeFine)) :
      completedFuture(succeeded(null));
  }

  private ActualCostRecord buildActualCostRecord(Loan loan, FeeFineOwner feeFineOwner,
    ItemLossType lossType, ZonedDateTime dateOfLoss, FeeFine actualCostFeeFine) {

    Item item = loan.getItem();

    Location permanentLocation = item.getPermanentLocation();
    return new ActualCostRecord()
      .withUserId(loan.getUserId())
      .withUserBarcode(loan.getUser().getBarcode())
      .withLoanId(loan.getId())
      .withItemLossType(lossType)
      .withDateOfLoss(dateOfLoss.toString())
      .withTitle(item.getTitle())
      .withIdentifiers(item.getIdentifiers()
        .collect(Collectors.toList()))
      .withItemBarcode(item.getBarcode())
      .withLoanType(item.getLoanTypeName())
      .withCallNumberComponents(item.getCallNumberComponents())
      .withPermanentItemLocation(permanentLocation == null || permanentLocation.getName() == null ?
        "" : permanentLocation.getName())
      .withFeeFineOwnerId(feeFineOwner.getId())
      .withFeeFineOwner(feeFineOwner.getOwner())
      .withFeeFineTypeId(actualCostFeeFine.getId())
      .withFeeFineType(actualCostFeeFine.getFeeFineType());
  }
}
