package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePointRepository {
  private final CollectionResourceClient servicePointsStorageClient;
  private final String SERVICE_POINT_TYPE = "servicepoint";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
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
  
  public CompletableFuture<HttpResult<MultipleRecords<Request>>> findServicePointsForRequests(
      MultipleRecords<Request> multipleRequests) {
    Collection<Request> requests = multipleRequests.getRecords();
    List<String> clauses = new ArrayList<>();
    
    for(Request request : requests) {
      if(request.getPickupServicePointId() != null) {
        String clause = String.format("id==%s", request.getPickupServicePointId());
        clauses.add(clause);
      }
    }
    
    if(clauses.isEmpty()) {
      log.info("No service points to query");
      return CompletableFuture.completedFuture(HttpResult.succeeded(multipleRequests));
    }
    
    final String spQuery = String.join(" OR ", clauses);
    log.info("Querying service points with query {}", spQuery);
    
    HttpResult<String> queryResult = CqlHelper.encodeQuery(spQuery);
    
    return queryResult.after(query -> servicePointsStorageClient.getMany(query)
        .thenApply(this::mapResponseToServicePoints)
        .thenApply(multipleServicePointsResult -> multipleServicePointsResult.next(
          multipleServicePoints -> {
            List<Request> newRequestList = new ArrayList<>();
            Collection<ServicePoint> spCollection = multipleServicePoints.getRecords();
            for(Request request : requests) {
              Request newRequest = null;
              Boolean foundSP = false; //Have we found a matching service point for the request?
              for(ServicePoint servicePoint : spCollection) { 
                if(request.getPickupServicePointId() != null &&
                    request.getPickupServicePointId().equals(servicePoint.getId())) {                
                  newRequest = request.withPickupServicePoint(servicePoint);
                  foundSP = true;
                  break;
                }
              }
              if(!foundSP) {
                log.info("No service point (out of {}) found for request {} (pickupServicePointId {})",
                    spCollection.size(), request.getId(), request.getPickupServicePointId());
                newRequest = request;
              }
              newRequestList.add(newRequest);
            }
            return HttpResult.succeeded(
              new MultipleRecords<>(newRequestList, multipleRequests.getTotalRecords()));
          })));    
  }
  
  private HttpResult<MultipleRecords<ServicePoint>> mapResponseToServicePoints(Response response) {
    return MultipleRecords.from(response, ServicePoint::from, "servicepoints");
  }
  
}
