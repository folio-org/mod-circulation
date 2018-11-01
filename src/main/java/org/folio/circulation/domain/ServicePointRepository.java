/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;

/**
 *
 * @author kurt
 */
public class ServicePointRepository {
  private final CollectionResourceClient servicePointsStorageClient;
  private final String SERVICE_POINT_TYPE = "servicepoint";
  
  public ServicePointRepository(Clients clients) {
    servicePointsStorageClient = clients.servicePointsStorage();
  }
  
  public CompletableFuture<HttpResult<ServicePoint>> getServicePointById(String id) {
    return FetchSingleRecord.<ServicePoint>forRecord(SERVICE_POINT_TYPE)
        .using(servicePointsStorageClient)
        .mapTo(ServicePoint::new)
        .whenNotFound(HttpResult.succeeded(null))
        .fetch(id);
  }
  
}
