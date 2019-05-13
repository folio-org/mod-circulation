package org.folio.circulation.domain;

import static org.folio.circulation.support.Result.of;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

class PatronGroupRepository {
  private final CollectionResourceClient patronGroupsStorageClient;

  PatronGroupRepository(Clients clients) {
    patronGroupsStorageClient = clients.patronGroupsStorage();
  }

  CompletableFuture<Result<Request>> findPatronGroupsForSingleRequestUsers(
    Result<Request> result) {

    return result.after(request -> {
      final ArrayList<String> groupsToFetch = getGroupsFromUsers(request);

      final MultipleRecordFetcher<PatronGroup> fetcher = createGroupsFetcher();

      return fetcher.findByIds(groupsToFetch)
        .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
          patronGroups -> of(() -> matchGroupsToUsers(request, patronGroups))));
    });
  }

  CompletableFuture<Result<MultipleRecords<Request>>> findPatronGroupsForRequestsUsers(
    MultipleRecords<Request> multipleRequests) {

    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> groupsToFetch = requests.stream()
      .map(this::getGroupsFromUsers)
      .flatMap(Collection::stream)
      .distinct()
      .collect(Collectors.toList());

    final MultipleRecordFetcher<PatronGroup> fetcher = createGroupsFetcher();

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

  private MultipleRecordFetcher<PatronGroup> createGroupsFetcher() {
    return new MultipleRecordFetcher<>(patronGroupsStorageClient,
      "usergroups", PatronGroup::from);
  }
}
