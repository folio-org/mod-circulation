package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.ActualCostStorageRepository;
import org.folio.circulation.support.results.Result;

import static org.folio.circulation.support.results.Result.ofAsync;
public class ActualCostRecordService {
  private final ActualCostStorageRepository actualCostStorageRepository;

  public ActualCostRecordService(ActualCostStorageRepository actualCostStorageRepository) {
    this.actualCostStorageRepository = actualCostStorageRepository;
  }

  public CompletableFuture<Result<Void>> createActualCostRecord() {
    return ofAsync()
  }
}
