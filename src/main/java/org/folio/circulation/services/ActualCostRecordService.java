package org.folio.circulation.services;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.support.results.Result;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_ID;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostRecordRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostRecordRepository) {
    this.actualCostRecordRepository = actualCostRecordRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createActualCostRecordIfNecessary(
    ReferenceDataContext referenceDataContext) {
    return createActualCostRecordIfNecessary(referenceDataContext.getLoan(),
      referenceDataContext.getFeeFineOwner(),
      ItemLossType.DECLARED_LOST, ZonedDateTime.now())
      .thenApply(mapResult(referenceDataContext::withActualCostRecord));
  }

  public CompletableFuture<Result<ActualCostRecord>> createActualCostRecordIfNecessary(
    Loan loan, FeeFineOwner feeFineOwner, ItemLossType itemLossType,
    ZonedDateTime dateOfLoss) {

    return loan.getLostItemPolicy().hasActualCostFee() ?
      actualCostRecordRepository.createActualCostRecord(
        buildActualCostRecord(loan, feeFineOwner, itemLossType, dateOfLoss))
      : completedFuture(succeeded(null));
  }

  private ActualCostRecord buildActualCostRecord(Loan loan, FeeFineOwner feeFineOwner,
    ItemLossType itemLossType, ZonedDateTime dateOfLoss) {

    Item item = loan.getItem();

    return new ActualCostRecord()
      .withUserId(loan.getUserId())
      .withUserBarcode(loan.getUser().getBarcode())
      .withLoanId(loan.getId())
      .withItemLossType(itemLossType)
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
      .withFeeFineTypeId(LOST_ITEM_ACTUAL_COST_FEE_ID)
      .withFeeFineType(LOST_ITEM_ACTUAL_COST_FEE_TYPE);
  }
}
