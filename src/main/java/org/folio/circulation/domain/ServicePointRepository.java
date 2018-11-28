package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  
  
  CompletableFuture<HttpResult<ServicePoint>> getServicePointById(String id) {
    log.info("Attempting to fetch service point with id {}", id);
    return FetchSingleRecord.<ServicePoint>forRecord(SERVICE_POINT_TYPE)
        .using(servicePointsStorageClient)
        .mapTo(ServicePoint::new)
        .whenNotFound(succeeded(null))
        .fetch(id);
  }
  
  public CompletableFuture<HttpResult<ServicePoint>> getServicePointForRequest(Request request) {
    return getServicePointById(request.getPickupServicePointId());
  } 
  
  public CompletableFuture<HttpResult<Loan>> findServicePointsForLoan(HttpResult<Loan> loanResult) {    
    return findCheckinServicePointForLoan(loanResult)
        .thenComposeAsync(this::findCheckoutServicePointForLoan);   
  }
  
  private CompletableFuture<HttpResult<Loan>> findCheckinServicePointForLoan(HttpResult<Loan> loanResult) {
    return loanResult.after(loan -> {
      String checkinServicePointId = loan.getCheckInServicePointId();
      if(checkinServicePointId == null) {
        return completedFuture(loanResult);
      }
      return getServicePointById(checkinServicePointId)
          .thenApply(servicePointResult ->
            servicePointResult.map(servicePoint -> {
              if(servicePoint == null) {
                log.info("No checkin servicepoint found for loan {}", loan.getId());
              } else {
                log.info("Checkin servicepoint with name {} found for loan {}",
                    servicePoint.getName(), loan.getId());
              }
              return loan.withCheckinServicePoint(servicePoint);
          }));
    });
  }
  
  private CompletableFuture<HttpResult<Loan>> findCheckoutServicePointForLoan(HttpResult<Loan> loanResult) {
    return loanResult.after(loan -> {
      String checkoutServicePointId = loan.getCheckoutServicePointId();
      if(checkoutServicePointId == null) {
        return completedFuture(loanResult);
      }
      return getServicePointById(checkoutServicePointId)
          .thenApply(servicePointResult ->
            servicePointResult.map(servicePoint -> {
              if(servicePoint == null) {
                log.info("No checkout servicepoint found for loan {}", loan.getId());
              } else {
                log.info("Checkout servicepoint with name {} found for loan {}",
                    servicePoint.getName(), loan.getId());
              }

              return loan.withCheckoutServicePoint(servicePoint);
          }));
      });
  }

  public CompletableFuture<HttpResult<MultipleRecords<Loan>>> findServicePointsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    Collection<Loan> loans = multipleLoans.getRecords();
    
    final List<String> servicePointsToFetch = 
        Stream.concat((loans.stream()
          .filter(Objects::nonNull)
          .map(Loan::getCheckInServicePointId)
          .filter(Objects::nonNull)
         ),
         (loans.stream()
          .filter(Objects::nonNull)
          .map(Loan::getCheckoutServicePointId)
          .filter(Objects::nonNull)
         )
       )
      .distinct()
      .collect(Collectors.toList());
    
    if(servicePointsToFetch.isEmpty()) {
      log.info("No service points to query for loans");
      return completedFuture(succeeded(multipleLoans));
    }
    
    String query = CqlHelper.multipleRecordsCqlQuery(servicePointsToFetch);
    
    return servicePointsStorageClient.getMany(query, loans.size(), 0)
        .thenApply(this::mapResponseToServicePoints)
        .thenApply(multipleServicePointsResult -> multipleServicePointsResult.next(
          multipleServicePoints -> {
            List<Loan> newLoanList = new ArrayList<>();
            Collection<ServicePoint> spCollection = multipleServicePoints.getRecords();
            for(Loan loan : loans) {
              Loan newLoan = loan;
              for(ServicePoint servicePoint : spCollection) {
                if(loan.getCheckInServicePointId() != null &&
                    loan.getCheckInServicePointId().equals(servicePoint.getId())) {
                  newLoan = newLoan.withCheckinServicePoint(servicePoint);
                }
                if(loan.getCheckoutServicePointId() != null &&
                    loan.getCheckoutServicePointId().equals(servicePoint.getId())) {
                  newLoan = newLoan.withCheckoutServicePoint(servicePoint);
                }
              }
              newLoanList.add(newLoan);
            }
            return succeeded(new MultipleRecords<>(newLoanList, multipleLoans.getTotalRecords()));
          }));
  }
  
  CompletableFuture<HttpResult<MultipleRecords<Request>>> findServicePointsForRequests(
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
      return completedFuture(succeeded(multipleRequests));
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

            return succeeded(
              new MultipleRecords<>(newRequestList, multipleRequests.getTotalRecords()));
          }));
  }
  
  private HttpResult<MultipleRecords<ServicePoint>> mapResponseToServicePoints(Response response) {
    return MultipleRecords.from(response, ServicePoint::from, "servicepoints");
  }
  
}
