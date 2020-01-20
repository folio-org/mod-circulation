package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.folio.circulation.domain.InTransitReportEntry;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.PatronGroupRepository;
import org.folio.circulation.domain.ReportRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.representations.ItemReportRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


public class ItemsInTransitResource extends Resource {

  private static final String ITEM_ID = "itemId";
  private final String rootPath;

  public ItemsInTransitResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final CollectionResourceClient loansStorageClient = clients.loansStorage();
    final CollectionResourceClient requestsStorageClient = clients.requestsStorage();
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final ReportRepository reportRepository = new ReportRepository(clients);
    final UserRepository userRepository = new UserRepository(clients);
    final PatronGroupRepository patronGroupRepository = new PatronGroupRepository(clients);
    final Comparator<InTransitReportEntry> sortByCheckinServicePointComparator = sortByCheckinServicePointComparator();

    reportRepository.getAllItemsByField("status.name", IN_TRANSIT.getValue())
      .thenComposeAsync(r -> r.after(itemsReportFetcher ->
        fetchItemsRelatedRecords(itemsReportFetcher, itemRepository, servicePointRepository)))
      .thenComposeAsync(r -> r.after(inTransitReportEntries ->
        fetchLoans(loansStorageClient, servicePointRepository, inTransitReportEntries,
          sortByCheckinServicePointComparator)))
      .thenComposeAsync(r -> findRequestsByItemsIds(requestsStorageClient, itemRepository,
        servicePointRepository, userRepository, patronGroupRepository, r.value()))
      .thenApply(this::mapResultToJson)
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> fetchItemsRelatedRecords(ItemsReportFetcher itemsReportFetcher,
                                                                                          ItemRepository itemRepository,
                                                                                          ServicePointRepository servicePointRepository) {
    List<Item> items = itemsReportFetcher.getResultListOfItems().stream()
      .flatMap(resultListOfItem -> resultListOfItem.value().getRecords().stream())
      .collect(Collectors.toList());

    return allOf(items, item -> fetchRelatedRecords(itemRepository, servicePointRepository, item))
    .thenApply(resultItem -> mapToInTransitReportEntries(resultItem.value()));
  }

  private Result<List<InTransitReportEntry>> mapToInTransitReportEntries(List<Item> items) {
    List<InTransitReportEntry> inTransitReportEntries=
      items.stream().map(InTransitReportEntry::new)
        .collect(Collectors.toList());

    return Result.succeeded(inTransitReportEntries);
  }

  private CompletableFuture<Result<Item>> fetchRelatedRecords(ItemRepository itemRepository,
                                                              ServicePointRepository servicePointRepository,
                                                              Item item) {
    return CompletableFuture.completedFuture(Result.succeeded(item))
      .thenComposeAsync(itemRepository::fetchItemRelatedRecords)
      .thenComposeAsync(result -> result
        .combineAfter(currentItem -> servicePointRepository
          .getServicePointById(currentItem.getInTransitDestinationServicePointId()),
          Item::updateDestinationServicePoint))
      .thenComposeAsync(result -> result
        .combineAfter(currentItem -> servicePointRepository
          .getServicePointById(currentItem.getLastCheckInServicePointId()),
          Item::updateLastCheckInServicePoint));
  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> findRequestsByItemsIds(CollectionResourceClient requestsStorageClient,
                                                                                       ItemRepository itemRepository,
                                                                                       ServicePointRepository servicePointRepository,
                                                                                       UserRepository userRepository,
                                                                                       PatronGroupRepository patronGroupRepository,
                                                                                       List<InTransitReportEntry> inTransitReportEntryList) {

    MultipleRecordFetcher<Request> fetcher = new MultipleRecordFetcher<>(requestsStorageClient, "requests", Request::from);

    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());
    Result<CqlQuery> cqlQueryResult = statusQuery.combine(statusQuery, CqlQuery::and)
      .map(q -> q.sortBy(ascending("position")));

    return fetcher.findByIndexNameAndQuery(mapToItemIdList(inTransitReportEntryList), ITEM_ID, cqlQueryResult)
      .thenComposeAsync(requests ->
        itemRepository.fetchItemsFor(requests, Request::withItem))
      .thenComposeAsync(result -> result.after(servicePointRepository::findServicePointsForRequests))
      .thenComposeAsync(result -> result.after(userRepository::findUsersForRequests))
      .thenComposeAsync(result -> result.after(patronGroupRepository::findPatronGroupsForRequestsUsers))
      .thenComposeAsync(r -> r.after(multipleRecords -> completedFuture(succeeded(
        multipleRecords.getRecords().stream().collect(
          Collectors.groupingBy(Request::getItemId))))))
      .thenComposeAsync(r -> mapRequestToInTransitReportEntry(inTransitReportEntryList, r.value()));
  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> fetchLoans(
    CollectionResourceClient loansStorageClient,
    ServicePointRepository servicePointRepository,
    List<InTransitReportEntry> inTransitReportEntries,
    Comparator<InTransitReportEntry> sortByCheckinServicePointComparator) {
    final List<String> itemsToFetchLoansFor = inTransitReportEntries.stream()
      .filter(Objects::nonNull)
      .map(inTransitReportEntry -> inTransitReportEntry.getItem().getItemId())
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if (itemsToFetchLoansFor.isEmpty()) {
      return completedFuture(succeeded(inTransitReportEntries));
    }

    final Result<CqlQuery> statusQuery = exactMatch("itemStatus",
      IN_TRANSIT.getValue());
    final Result<CqlQuery> itemIdQuery = exactMatchAny(ITEM_ID,
      itemsToFetchLoansFor);

    CompletableFuture<Result<MultipleRecords<Loan>>> multipleRecordsLoans =
      statusQuery.combine(
        itemIdQuery, CqlQuery::and)
        .after(q -> loansStorageClient.getMany(q,
          PageLimit.limit(inTransitReportEntries.size())))
        .thenApply(result -> result.next(this::mapResponseToLoans));

    return multipleRecordsLoans.thenCompose(multiLoanRecordsResult ->
      multiLoanRecordsResult.after(
        servicePointRepository::findServicePointsForLoans))
      .thenApply(multipleLoansResult -> multipleLoansResult.next(
        loans -> matchLoansToInTransitReportEntry(inTransitReportEntries, loans,
          sortByCheckinServicePointComparator)));
  }

  private Result<List<InTransitReportEntry>> matchLoansToInTransitReportEntry(
    List<InTransitReportEntry> inTransitReportEntries,
    MultipleRecords<Loan> loans,
    Comparator<InTransitReportEntry> sortByCheckinServicePointComparator) {

    return of(() ->
      inTransitReportEntries.stream()
        .map(inTransitReportEntry -> matchLoansToInTransitReportEntry(inTransitReportEntry, loans))
        .sorted(sortByCheckinServicePointComparator)
        .collect(Collectors.toList()));
  }

  private InTransitReportEntry matchLoansToInTransitReportEntry(
    InTransitReportEntry inTransitReportEntry,
    MultipleRecords<Loan> loans) {

    final Map<String, Loan> loanMap = loans.toMap(Loan::getItemId);
    inTransitReportEntry
      .setLoan(loanMap.getOrDefault(inTransitReportEntry.getItem().getItemId(), null));
    return inTransitReportEntry;
  }

  private Result<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    return MultipleRecords.from(response, Loan::from, "loans");
  }

  private List<String> mapToItemIdList(List<InTransitReportEntry> inTransitReportEntryList) {
    return inTransitReportEntryList.stream()
      .map(InTransitReportEntry::getItem)
      .filter(Objects::nonNull)
      .map(Item::getItemId)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

  }

  private CompletableFuture<Result<List<InTransitReportEntry>>> mapRequestToInTransitReportEntry(
    List<InTransitReportEntry> inTransitReportEntryList,
    Map<String, List<Request>> itemRequestsMap) {

    inTransitReportEntryList.stream().filter(inTransitReportEntry -> itemRequestsMap
      .containsKey(inTransitReportEntry.getItem().getItemId()))
      .forEach(inTransitReportEntry -> inTransitReportEntry.setRequest(itemRequestsMap
        .get(inTransitReportEntry.getItem().getItemId()).stream().findFirst()
        .orElse(null)));

    return CompletableFuture.completedFuture(Result.succeeded(inTransitReportEntryList));
  }

  private Result<JsonObject> mapResultToJson
    (Result<List<InTransitReportEntry>> inTransitReportEntry) {

    return inTransitReportEntry.map(resultList -> resultList
      .stream().map(itemAndRelatedRecord -> new ItemReportRepresentation()
        .createItemReport(itemAndRelatedRecord))
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add)))
      .next(jsonArray -> Result.succeeded(new JsonObject()
        .put("items", jsonArray)
        .put("totalRecords", jsonArray.size())));
  }

  private Comparator<InTransitReportEntry> sortByCheckinServicePointComparator() {
    return Comparator.comparing(inTransitReportEntry-> Optional.ofNullable(inTransitReportEntry
      .getLoan()).map(loan -> Optional.ofNullable(loan.getCheckinServicePoint())
      .map(ServicePoint::getName).orElse(null))
      .orElse(null), Comparator.nullsLast(String::compareTo));
  }

}
