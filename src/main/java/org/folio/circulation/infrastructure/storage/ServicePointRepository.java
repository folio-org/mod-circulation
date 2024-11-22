package org.folio.circulation.infrastructure.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.storage.mappers.ServicePointMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.fetching.MultipleCqlIndexValuesCriteria;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

public class ServicePointRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient servicePointsStorageClient;

  public ServicePointRepository(Clients clients) {
    this(clients, false);
  }

  public ServicePointRepository(Clients clients, boolean includeRoutingServicePoints) {
    if (includeRoutingServicePoints) {
      servicePointsStorageClient = clients.routingServicePointsStorage();
    } else {
      servicePointsStorageClient = clients.servicePointsStorage();
    }
  }

  public CompletableFuture<Result<ServicePoint>> getServicePointById(UUID id) {
    log.debug("getServicePointById:: parameters id: {}", id);
    if(id == null) {
      log.info("getServicePointById:: id is null");
      return ofAsync(() -> null);
    }

    return getServicePointById(id.toString());
  }

  public CompletableFuture<Result<ServicePoint>> getServicePointById(String id) {
    log.debug("getServicePointById:: parameters id: {}", id);
    if(id == null) {
      log.info("getServicePointById:: id is null");
      return ofAsync(() -> null);
    }

    final var mapper = new ServicePointMapper();

    return FetchSingleRecord.<ServicePoint>forRecord("service point")
        .using(servicePointsStorageClient)
        .mapTo(mapper::toDomain)
        .whenNotFound(succeeded(null))
        .fetch(id);
  }

  public CompletableFuture<Result<ServicePoint>> getServicePointForRequest(Request request) {
    log.debug("getServicePointForRequest:: parameters request: {}", request);
    return getServicePointById(request.getPickupServicePointId());
  }

  public CompletableFuture<Result<Loan>> findServicePointsForLoan(Result<Loan> loanResult) {
    log.debug("findServicePointsForLoan:: parameters loanResult: {}", loanResult);
    return fetchCheckInServicePoint(loanResult)
      .thenComposeAsync(this::fetchCheckOutServicePoint);
  }

  private CompletableFuture<Result<Loan>> fetchCheckOutServicePoint(Result<Loan> loanResult) {
    log.debug("fetchCheckOutServicePoint:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult
      .combineAfter(loan -> getServicePointById(loan.getCheckoutServicePointId()),
        Loan::withCheckoutServicePoint);
  }

  private CompletableFuture<Result<Loan>> fetchCheckInServicePoint(Result<Loan> loanResult) {
    log.debug("fetchCheckInServicePoint:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult
      .combineAfter(loan -> getServicePointById(loan.getCheckInServicePointId()),
        Loan::withCheckinServicePoint);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findServicePointsForLoans(
    MultipleRecords<Loan> multipleLoans) {

    log.debug("findServicePointsForLoans:: parameters multipleLoans: {}", () -> multipleRecordsAsString(multipleLoans));

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

    final FindWithMultipleCqlIndexValues<ServicePoint> fetcher = createServicePointsFetcher();

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

  public CompletableFuture<Result<MultipleRecords<Request>>> findServicePointsForRequests(
    MultipleRecords<Request> multipleRequests) {

    log.debug("findServicePointsForRequests:: parameters multipleRequests: {}", () -> multipleRecordsAsString(multipleRequests));

    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> servicePointsToFetch = requests.stream()
      .filter(Objects::nonNull)
      .map(Request::getPickupServicePointId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if(servicePointsToFetch.isEmpty()) {
      log.info("findServicePointsForRequests:: No service points to query");
      return completedFuture(succeeded(multipleRequests));
    }

    final FindWithMultipleCqlIndexValues<ServicePoint> fetcher = createServicePointsFetcher();

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

            return succeeded(new MultipleRecords<>(newRequestList,
              multipleRequests.getTotalRecords()));
          }));
  }

  public CompletableFuture<Result<Collection<ServicePoint>>> findServicePointsByIds(
    Collection<String> ids) {

    log.debug("findServicePointsByIds:: parameters ids: {}", () -> collectionAsString(ids));

    return createServicePointsFetcher().findByIds(ids)
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<Collection<ServicePoint>>> fetchServicePointsByIndexName(
    String indexName) {

    return createServicePointsFetcher().find(MultipleCqlIndexValuesCriteria.builder()
        .indexName(indexName)
        .indexOperator(CqlQuery::matchAny)
        .value("true")
        .build())
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<Collection<ServicePoint>>>
  fetchPickupLocationServicePointsByIdsAndIndexName(Set<String> ids, String indexName) {

    log.debug("filterIdsByServicePointsAndPickupLocationExistence:: parameters ids: {}, " +
        "indexName: {}", () -> collectionAsString(ids), () -> indexName);

    Result<CqlQuery> pickupLocationQuery = exactMatch(indexName, "true");

    return createServicePointsFetcher().findByIdIndexAndQuery(ids, "id", pickupLocationQuery)
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private FindWithMultipleCqlIndexValues<ServicePoint> createServicePointsFetcher() {
    final var mapper = new ServicePointMapper();

    return findWithMultipleCqlIndexValues(servicePointsStorageClient,
      "servicepoints", mapper::toDomain);
  }
}
