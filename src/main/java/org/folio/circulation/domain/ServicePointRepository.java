package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePointRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String SERVICE_POINT_TYPE = "servicepoint";

  private final CollectionResourceClient servicePointsStorageClient;

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
  
  public CompletableFuture<HttpResult<ServicePoint>> getServicePointForRequest(Request request) {
    return getServicePointById(request.getPickupServicePointId());
  } 
  
  public CompletableFuture<HttpResult<Loan>> findServicePointsForLoan(Loan loan) {
    return findCheckinServicePointForLoan(loan)
        .thenComposeAsync(loanResult -> {
          return findCheckoutServicePointForLoan(loanResult.value());
        });
  }
  
  public CompletableFuture<HttpResult<Loan>> findCheckinServicePointForLoan(Loan loan) {
    String checkinServicePointId = loan.getCheckinServicePointId();
    if(checkinServicePointId == null) {
      return CompletableFuture.completedFuture(HttpResult.succeeded(loan));
    }
    return getServicePointById(checkinServicePointId)
        .thenApply(servicePointResult -> {
          return servicePointResult.map(servicePoint -> {
            Loan newLoan = loan.withCheckinServicePoint(servicePoint);
            return newLoan;
          });
        });
  }
  
   public CompletableFuture<HttpResult<Loan>> findCheckoutServicePointForLoan(Loan loan) {
    String checkoutServicePointId = loan.getCheckoutServicePointId();
    if(checkoutServicePointId == null) {
      return CompletableFuture.completedFuture(HttpResult.succeeded(loan));
    }
    return getServicePointById(checkoutServicePointId)
        .thenApply(servicePointResult -> {
          return servicePointResult.map(servicePoint -> {
            Loan newLoan = loan.withCheckoutServicePoint(servicePoint);
            return newLoan;
          });
        });
  }
  
  public CompletableFuture<HttpResult<MultipleRecords<Request>>> findServicePointsForRequests(
      MultipleRecords<Request> multipleRequests) {
    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> servicePointsToFetch = requests.stream()
      .filter(Objects::nonNull)
      .map(Request::getPickupServicePointId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if(servicePointsToFetch.isEmpty()) {
      log.info("No service points to query");
      return CompletableFuture.completedFuture(HttpResult.succeeded(multipleRequests));
    }
    
    String query = CqlHelper.multipleRecordsCqlQuery(servicePointsToFetch);

    return servicePointsStorageClient.getMany(query, requests.size(), 0)
        .thenApply(this::mapResponseToServicePoints)
        .thenApply(multipleServicePointsResult -> multipleServicePointsResult.next(
          multipleServicePoints -> {
            List<Request> newRequestList = new ArrayList<>();
            Collection<ServicePoint> spCollection = multipleServicePoints.getRecords();
            for(Request request : requests) {
              Request newRequest = null;
              boolean foundSP = false; //Have we found a matching service point for the request?
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
          }));
  }
  
  private HttpResult<MultipleRecords<ServicePoint>> mapResponseToServicePoints(Response response) {
    return MultipleRecords.from(response, ServicePoint::from, "servicepoints");
  }
  
}
