package org.folio.circulation.services;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.AllowedServicePointsRequest;
import org.folio.circulation.support.results.Result;

public class AllowedServicePointsService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePoints(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePoints:: parameters: request={}", request);

    return request.getItemId() != null
      ? getAllowedServicePointsForItem(request)
      : getAllowedServicePointsForInstance(request);
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePointsForItem(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForItem:: parameters: request={}", request);

    return ofAsync(new EnumMap<>(RequestType.class));
  }

  private CompletableFuture<Result<Map<RequestType, Set<String>>>> getAllowedServicePointsForInstance(
    AllowedServicePointsRequest request) {

    log.debug("getAllowedServicePointsForInstance:: parameters: request={}", request);

    return ofAsync(new EnumMap<>(RequestType.class));
  }

}
