package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePointRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient servicePointsStorageClient;
  private final ConfigurationRepository configurationRepository;

  public ServicePointRepository(Clients clients) {
    servicePointsStorageClient = clients.servicePointsStorage();
    configurationRepository = new ConfigurationRepository(clients);
  }

  public CompletableFuture<Result<ServicePoint>> getServicePointById(UUID id) {
    if(id == null) {
      return ofAsync(() -> null);
    }

    return getServicePointById(id.toString());
  }

  CompletableFuture<Result<ServicePoint>> getServicePointById(String id) {
    if(id == null) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<ServicePoint>forRecord("service point")
        .using(servicePointsStorageClient)
        .mapTo(ServicePoint::new)
        .whenNotFound(succeeded(null))
        .fetch(id);
  }

  CompletableFuture<Result<ServicePoint>> getServicePointWithTenantTimeZone(String id) {
    return getServicePointById(id)
      .thenCombineAsync(
        configurationRepository.findTimeZoneConfiguration(),
        this::processServicePointAndTimeZoneResults
      );
  }

  private Result<ServicePoint> processServicePointAndTimeZoneResults(Result<ServicePoint> servicePointResult, Result<DateTimeZone> tenantTimeZoneResult) {
    return servicePointResult.combineToResult(tenantTimeZoneResult, (servicePoint, tenantTimeZone) -> {
      if (servicePoint == null) {
        log.info("Service point is null");
        return succeeded(null);
      }

      return succeeded(servicePoint.withTenantTimeZone(tenantTimeZone));
    });
  }

  public CompletableFuture<Result<ServicePoint>> getServicePointForRequest(Request request) {
    return getServicePointById(request.getPickupServicePointId());
  }

  public CompletableFuture<Result<Loan>> findServicePointsForLoan(Result<Loan> loanResult) {
    return fetchCheckInServicePoint(loanResult)
      .thenComposeAsync(this::fetchCheckOutServicePoint);
  }

  private CompletableFuture<Result<Loan>> fetchCheckOutServicePoint(Result<Loan> loanResult) {
    return loanResult
      .combineAfter(loan -> getServicePointById(loan.getCheckoutServicePointId()),
        Loan::withCheckoutServicePoint);
  }

  private CompletableFuture<Result<Loan>> fetchCheckInServicePoint(Result<Loan> loanResult) {
    return loanResult
      .combineAfter(loan -> getServicePointById(loan.getCheckInServicePointId()),
        Loan::withCheckinServicePoint);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findServicePointsForLoans(
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

    final MultipleRecordFetcher<ServicePoint> fetcher = createServicePointsFetcher();

    return fetcher.findByIds(servicePointsToFetch)
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

  CompletableFuture<Result<MultipleRecords<Request>>> findServicePointsForRequests(
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

    final MultipleRecordFetcher<ServicePoint> fetcher = createServicePointsFetcher();

    return fetcher.findByIds(servicePointsToFetch)
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

  private MultipleRecordFetcher<ServicePoint> createServicePointsFetcher() {
    return new MultipleRecordFetcher<>(
      servicePointsStorageClient, "servicepoints", ServicePoint::from);
  }
}
