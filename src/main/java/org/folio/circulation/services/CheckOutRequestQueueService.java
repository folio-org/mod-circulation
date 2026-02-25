package org.folio.circulation.services;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
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

    log.debug("isTitleLevelRequestFulfillableByItem:: parameters itemId: {}, requestId: {}",
      item::getItemId, request::getId);

    if (!StringUtils.equals(request.getInstanceId(), item.getInstanceId())) {
      log.info("isTitleLevelRequestFulfillableByItem:: instanceId mismatch, not fulfillable");
      return ofAsync(false);
    }

    if (request.isRecall()) {
      log.info("isTitleLevelRequestFulfillableByItem:: recall request, checking itemId match");
      return ofAsync(StringUtils.equals(request.getItemId(), item.getItemId()));
    }

    return canRequestBeFulfilledByItem(item, request);
  }
}
