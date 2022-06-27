package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;

public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostRecordRepository;
  private final LocationRepository locationRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostRecordRepository,
    LocationRepository locationRepository) {
    this.actualCostRecordRepository = actualCostRecordRepository;
    this.locationRepository = locationRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createIfNecessaryForDeclaredLostItem(
    ReferenceDataContext referenceDataContext) {

    return completedFuture(succeeded(referenceDataContext));
  }

  public CompletableFuture<Result<LoanToChargeFees>> createIfNecessaryForAgedToLostItem(
    LoanToChargeFees loanToChargeFees) {

    return completedFuture(succeeded(loanToChargeFees));
  }
}
