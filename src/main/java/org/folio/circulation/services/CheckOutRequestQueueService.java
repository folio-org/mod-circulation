package org.folio.circulation.services;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class CheckOutRequestQueueService extends RequestQueueService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public CheckOutRequestQueueService(RequestPolicyRepository requestPolicyRepository,
    LoanPolicyRepository loanPolicyRepository) {

    super(requestPolicyRepository, loanPolicyRepository);
  }

  public static CheckOutRequestQueueService using(Clients clients) {
    return new CheckOutRequestQueueService(
      new RequestPolicyRepository(clients),
      new LoanPolicyRepository(clients)
    );
  }

  @Override
  protected CompletableFuture<Result<Boolean>> isTitleLevelRequestFulfillableByItem(Item item,
    Request request) {

    log.info("isTitleLevelRequestFulfillableByItem:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);

    var tlrRequestsFeatureEnabled = Optional.ofNullable(request.getTlrSettingsConfiguration())
      .map(TlrSettingsConfiguration::isTitleLevelRequestsFeatureEnabled)
      .orElse(false);
    boolean sameInstance = Strings.CS.equals(request.getInstanceId(), item.getInstanceId());

    // MCBFF-211 diagnostics: this is the CIRC-2644 ECS/DCB fulfillability path used
    // specifically during checkout. requestLevel/requestType/isRecall + sameInstance +
    // tlrRequestsFeatureEnabled + item.isDcbItem() together decide whether THIS request can
    // be matched to the item being checked out. An asymmetry here (Recall requires exact
    // itemId match; Hold/Page use looser matching) could cause a Page at the head of the
    // queue to be skipped while a later Recall gets selected instead.
    log.info("MCBFF-211 isTitleLevelRequestFulfillableByItem(checkout):: requestId: {}, " +
        "requestType: {}, itemId: {}, requestInstanceId: {}, itemInstanceId: {}, " +
        "sameInstance: {}, tlrFeatureEnabled: {}, itemIsDcbItem: {}, isRecall: {}",
      request.getId(), request.getRequestType(), request.getItemId(), request.getInstanceId(),
      item.getInstanceId(), sameInstance, tlrRequestsFeatureEnabled, item.isDcbItem(),
      request.isRecall());

    if (!sameInstance && (!tlrRequestsFeatureEnabled || !item.isDcbItem())) {
      log.info("isTitleLevelRequestFulfillableByItem:: instanceId mismatch, not fulfillable");
      log.info("MCBFF-211 isTitleLevelRequestFulfillableByItem(checkout):: requestId: {} -> " +
        "NOT fulfillable (instance mismatch and not DCB/TLR eligible)", request.getId());
      return ofAsync(false);
    }

    if (request.isRecall()) {
      boolean matches = Strings.CS.equals(request.getItemId(), item.getItemId());
      log.info("isTitleLevelRequestFulfillableByItem:: recall request, checking itemId match");
      log.info("MCBFF-211 isTitleLevelRequestFulfillableByItem(checkout):: requestId: {} is a " +
        "RECALL -> fulfillable={} (exact itemId match required)", request.getId(), matches);
      return ofAsync(matches);
    }

    return canRequestBeFulfilledByItem(item, request);
  }
}
