package org.folio.circulation.services;

import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.support.results.Result;

public class DeclareLostService {
  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final LocationRepository locationRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;

  public DeclareLostService(LostItemPolicyRepository lostItemPolicyRepository,
    LocationRepository locationRepository, FeeFineOwnerRepository feeFineOwnerRepository) {

    this.lostItemPolicyRepository = lostItemPolicyRepository;
    this.locationRepository = locationRepository;
    this.feeFineOwnerRepository = feeFineOwnerRepository;
  }

  public CompletableFuture<Result<DeclareLostContext>> fetchLostItemPolicy(
    Result<DeclareLostContext> contextResult) {

    return contextResult.combineAfter(
      context -> lostItemPolicyRepository.getLostItemPolicyById(
        context.getLoan().getLostItemPolicyId()), DeclareLostContext::withLostItemPolicy);
  }

  public CompletableFuture<Result<DeclareLostContext>> fetchFeeFineOwner(
    DeclareLostContext declareLostContext) {

    return locationRepository.fetchLocationById(declareLostContext.getLoan().getItem()
        .getPermanentLocationId())
      .thenApply(mapResult(Location::getPrimaryServicePointId))
      .thenCompose(r -> r.after(feeFineOwnerRepository::findOwnerForServicePoint))
      .thenApply(mapResult(declareLostContext::withFeeFineOwner));
  }
}
