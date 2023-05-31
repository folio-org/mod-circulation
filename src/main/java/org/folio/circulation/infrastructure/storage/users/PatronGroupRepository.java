package org.folio.circulation.infrastructure.storage.users;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.PatronGroup.unknown;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.results.Result;

public class PatronGroupRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient patronGroupsStorageClient;

  public PatronGroupRepository(Clients clients) {
    patronGroupsStorageClient = clients.patronGroupsStorage();
  }

  public CompletableFuture<Result<Request>> findPatronGroupsForSingleRequestUsers(
    Result<Request> result) {

    log.debug("findPatronGroupsForSingleRequestUsers:: parameters result: {}", () -> resultAsString(result));
    return result.after(request -> {
      final ArrayList<String> groupsToFetch = getGroupsFromUsers(request);

      final FindWithMultipleCqlIndexValues<PatronGroup> fetcher = createGroupsFetcher();

      return fetcher.findByIds(groupsToFetch)
        .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
          patronGroups -> of(() -> matchGroupsToUsers(request, patronGroups))));
    });
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findPatronGroupsForRequestsUsers(
    MultipleRecords<Request> multipleRequests) {

    log.debug("findPatronGroupsForRequestsUsers:: parameters multipleRequests: {}", () -> multipleRecordsAsString(multipleRequests));
    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> groupsToFetch = requests.stream()
      .map(this::getGroupsFromUsers)
      .flatMap(Collection::stream)
      .distinct()
      .collect(Collectors.toList());

    final FindWithMultipleCqlIndexValues<PatronGroup> fetcher = createGroupsFetcher();

    return fetcher.findByIds(groupsToFetch)
      .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
        patronGroups -> matchGroupsToUsers(multipleRequests, patronGroups)));
  }

  public CompletableFuture<Result<Collection<PatronGroup>>> findPatronGroupsByIds(
    Collection<String> ids) {

    log.debug("findPatronGroupsByIds:: parameters ids: {}", () -> collectionAsString(ids));
    return createGroupsFetcher().findByIds(ids)
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  private ArrayList<String> getGroupsFromUsers(Request request) {
    log.debug("getGroupsFromUsers:: parameters request: {}", request);
    final ArrayList<String> groupsToFetch = new ArrayList<>();

    if(request.getRequester() != null) {
      log.info("getGroupsFromUsers:: adding requester group to fetch");
      groupsToFetch.add(request.getRequester().getPatronGroupId());
    }

    if(request.getProxy() != null) {
      log.info("getGroupsFromUsers:: adding proxy group to fetch");
      groupsToFetch.add(request.getProxy().getPatronGroupId());
    }

    return groupsToFetch;
  }

  private Request matchGroupsToUsers(
    Request request,
    MultipleRecords<PatronGroup> patronGroups) {

    log.debug("matchGroupsToUsers:: parameters request: {}, patronGroups: {}", () -> request, () -> multipleRecordsAsString(patronGroups));

    final Map<String, PatronGroup> groupMap = patronGroups.toMap(PatronGroup::getId);

    return request
      .withRequester(addGroupToUser(request.getRequester(), groupMap))
      .withProxy(addGroupToUser(request.getProxy(), groupMap));
  }

  private Result<MultipleRecords<Request>> matchGroupsToUsers(
    MultipleRecords<Request> requests,
    MultipleRecords<PatronGroup> patronGroups) {

    log.debug("matchGroupsToUsers:: parameters requests: {}, patronGroups: {}", () -> multipleRecordsAsString(requests), () -> multipleRecordsAsString(patronGroups));

    return of(() ->
      requests.mapRecords(request -> matchGroupsToUsers(request, patronGroups)));
  }

  private User addGroupToUser(User user, Map<String, PatronGroup> groupMap) {
    if(user == null) {
      return user;
    }

    return user.withPatronGroup(
      groupMap.getOrDefault(user.getPatronGroupId(), null));
  }

  public CompletableFuture<Result<Loan>> findGroupForLoan(Result<Loan> loanResult) {
    log.debug("findGroupForLoan:: parameters loanResult: {}", () -> resultAsString(loanResult));
    return loanResult.combineAfter(loan ->
      getPatronGroupById(loan.getPatronGroupIdAtCheckout()), Loan::withPatronGroupAtCheckout);
  }

  public CompletableFuture<Result<User>> findGroupForUser(User user) {
    log.debug("findGroupForUser:: parameters user: {}", user);
    return getPatronGroupById(user.getPatronGroupId())
      .thenApply(r -> r.map(user::withPatronGroup));
  }

  public CompletableFuture<Result<User>> findGroupForUser(Result<User> user) {
    log.debug("findGroupForUser:: parameters user: {}", () -> resultAsString(user));
      return user.combineAfter(user1 -> isNull(user1) ? completedFuture(succeeded(unknown(null))) :
        getPatronGroupById(user1.getPatronGroupId()), User::withPatronGroup);

  }

  private CompletableFuture<Result<PatronGroup>> getPatronGroupById(String groupId) {
    log.debug("getPatronGroupById:: parameters groupId: {}", groupId);
    if(isNull(groupId)) {
      log.info("getPatronGroupById:: patron group id is null");
      return ofAsync(() -> unknown(null));
    }

    return FetchSingleRecord.<PatronGroup>forRecord("patron group")
      .using(patronGroupsStorageClient)
      .mapTo(PatronGroup::from)
      .whenNotFound(succeeded(unknown(groupId)))
      .fetch(groupId);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> findPatronGroupForLoanAndRelatedRecords(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("findPatronGroupForLoanAndRelatedRecords:: parameters loanAndRelatedRecords: {}", loanAndRelatedRecords);
    final FindWithMultipleCqlIndexValues<PatronGroup> fetcher = createGroupsFetcher();
    return fetcher.findByIds(Collections.singleton(loanAndRelatedRecords.getLoan()
      .getUser().getPatronGroupId()))
      .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
        patronGroups -> of(() -> matchGroupToUser(loanAndRelatedRecords, patronGroups))));
  }

  private LoanAndRelatedRecords matchGroupToUser(
    LoanAndRelatedRecords loanAndRelatedRecords,
    MultipleRecords<PatronGroup> patronGroups) {

    log.debug("matchGroupToUser:: parameters loanAndRelatedRecords: {}, patronGroups: {}", () ->  loanAndRelatedRecords, () -> multipleRecordsAsString(patronGroups));

    final Map<String, PatronGroup> groupMap = patronGroups.toMap(PatronGroup::getId);

   return loanAndRelatedRecords.withLoan(loanAndRelatedRecords.getLoan()
      .withUser(addGroupToUser(loanAndRelatedRecords.getLoan().getUser(),groupMap)));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findPatronGroupsByIds(
    MultipleRecords<Loan> multipleLoans) {

    log.debug("findPatronGroupsByIds:: parameters multipleLoans: {}", () -> multipleRecordsAsString(multipleLoans));
    Collection<Loan> loans = multipleLoans.getRecords();

    final Collection<String> patronGroupsToFetch =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getPatronGroupIdAtCheckout)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    if(patronGroupsToFetch.isEmpty()){
      log.info("findPatronGroupsByIds: no patron groups to fetch");
      return completedFuture(succeeded(multipleLoans));
    }

    final FindWithMultipleCqlIndexValues<PatronGroup> fetcher = createGroupsFetcher();

    return fetcher.findByIds(patronGroupsToFetch)
      .thenApply(mapResult(groups -> groups.toMap(PatronGroup::getId)))
      .thenApply(mapResult(groups -> setPatronGroups(loans, groups)))
      .thenApply(mapResult(collection -> new MultipleRecords<>(collection, multipleLoans.getTotalRecords())));
  }

  private Collection<Loan> setPatronGroups(Collection<Loan> loans, Map<String, PatronGroup> patronGroups) {
    return loans.stream()
      .map(loan -> loan.withPatronGroupAtCheckout(patronGroups.get(loan.getPatronGroupIdAtCheckout())))
      .collect(Collectors.toList());
  }

  private FindWithMultipleCqlIndexValues<PatronGroup> createGroupsFetcher() {
    return findWithMultipleCqlIndexValues(patronGroupsStorageClient,
      "usergroups", PatronGroup::from);
  }
}
