package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.PatronGroup.unknown;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.Result;


public class PatronGroupRepository {
  private final CollectionResourceClient patronGroupsStorageClient;

  public PatronGroupRepository(Clients clients) {
    patronGroupsStorageClient = clients.patronGroupsStorage();
  }

  CompletableFuture<Result<Request>> findPatronGroupsForSingleRequestUsers(
    Result<Request> result) {

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

  private ArrayList<String> getGroupsFromUsers(Request request) {
    final ArrayList<String> groupsToFetch = new ArrayList<>();

    if(request.getRequester() != null) {
      groupsToFetch.add(request.getRequester().getPatronGroupId());
    }

    if(request.getProxy() != null) {
      groupsToFetch.add(request.getProxy().getPatronGroupId());
    }

    return groupsToFetch;
  }

  private Request matchGroupsToUsers(
    Request request,
    MultipleRecords<PatronGroup> patronGroups) {

    final Map<String, PatronGroup> groupMap = patronGroups.toMap(PatronGroup::getId);

    return request
      .withRequester(addGroupToUser(request.getRequester(), groupMap))
      .withProxy(addGroupToUser(request.getProxy(), groupMap));
  }

  private Result<MultipleRecords<Request>> matchGroupsToUsers(
    MultipleRecords<Request> requests,
    MultipleRecords<PatronGroup> patronGroups) {

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
    return loanResult.combineAfter(loan ->
      getPatronGroupById(loan.getPatronGroupIdAtCheckout()), Loan::withPatronGroupAtCheckout);
  }

  private CompletableFuture<Result<PatronGroup>> getPatronGroupById(String groupId) {
    if(isNull(groupId)) {
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
    final FindWithMultipleCqlIndexValues<PatronGroup> fetcher = createGroupsFetcher();
    return fetcher.findByIds(Collections.singleton(loanAndRelatedRecords.getLoan()
      .getUser().getPatronGroupId()))
      .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
        patronGroups -> of(() -> matchGroupToUser(loanAndRelatedRecords, patronGroups))));
  }

  private LoanAndRelatedRecords matchGroupToUser(
    LoanAndRelatedRecords loanAndRelatedRecords,
    MultipleRecords<PatronGroup> patronGroups) {

    final Map<String, PatronGroup> groupMap = patronGroups.toMap(PatronGroup::getId);

   return loanAndRelatedRecords.withLoan(loanAndRelatedRecords.getLoan()
      .withUser(addGroupToUser(loanAndRelatedRecords.getLoan().getUser(),groupMap)));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findPatronGroupsByIds(
    MultipleRecords<Loan> multipleLoans) {
    Collection<Loan> loans = multipleLoans.getRecords();

    final Collection<String> patronGroupsToFetch =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getPatronGroupIdAtCheckout)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    if(patronGroupsToFetch.isEmpty()){
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
