package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;

public class UpdatePickupServicePoint {
  private final ServicePointRepository servicePointRepository;
  
  public UpdatePickupServicePoint(Clients clients) {
    servicePointRepository = new ServicePointRepository(clients);
  }
  
  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> onRequestCreation(
      RequestAndRelatedRecords rarr) {
    Request request = rarr.getRequest();
    return servicePointRepository.getServicePointById(request.getId())        
        .thenApply( servicePointResult -> {
          ServicePoint servicePoint = servicePointResult.value();
          Request newRequest = request.withPickupServicePoint(servicePoint);
          RequestAndRelatedRecords newRarr = rarr.withRequest(newRequest);
          return HttpResult.succeeded(newRarr);
        });
  }

}


