package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Limit;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRepository {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String USERS_RECORD_PROPERTY = "users";

  private final CollectionResourceClient usersStorageClient;

  public UserRepository(Clients clients) {
    usersStorageClient = clients.usersStorage();
  }

  public CompletableFuture<Result<User>> getUser(UserRelatedRecord userRelatedRecord) {
    return getUser(userRelatedRecord.getUserId());
  }

  public CompletableFuture<Result<User>> getProxyUser(UserRelatedRecord userRelatedRecord) {
    if (userRelatedRecord.getProxyUserId() == null) {
      return CompletableFuture.completedFuture(succeeded(null));
    }

    return getUser(userRelatedRecord.getProxyUserId());
  }

  public CompletableFuture<Result<User>> getUser(String userId) {
    if(isNull(userId)) {
      return ofAsync(() -> null);
    }

    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(succeeded(null))
      .fetch(userId);
  }

  public CompletableFuture<Result<Loan>> findUserForLoan(Result<Loan> loanResult) {
    return loanResult.after(loan -> getUser(loan.getUserId())
      .thenApply(userResult ->
        userResult.map(user -> {
          if (isNull(user)) {
            log.info("No user found for loan {}", loan.getId());
          } else {
            log.info("User with username {} found for loan {}",
              loan.getUser().getUsername(), loan.getId());
          }
          return loan.withUser(user);
        })));
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findUsersForLoans(
    MultipleRecords<Loan> multipleLoans) {

    Collection<Loan> loans = multipleLoans.getRecords();

    return getUsersForLoans(loans)
      .thenApply(r -> r.map(users -> multipleLoans.mapRecords(
        loan -> loan.withUser(users.getOrDefault(loan.getUserId(), null)))));
  }

  private CompletableFuture<Result<Map<String, User>>> getUsersForLoans(
    Collection<Loan> loans) {

    final List<String> usersToFetch =
      loans.stream()
        .filter(Objects::nonNull)
        .map(Loan::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    final MultipleRecordFetcher<User> fetcher = createUsersFetcher();

    return fetcher.findByIds(usersToFetch)
      .thenApply(mapResult(users -> users.toMap(User::getId)));
  }

  private MultipleRecordFetcher<User> createUsersFetcher() {
    return new MultipleRecordFetcher<>(usersStorageClient, USERS_RECORD_PROPERTY, User::from);
  }

  //TODO: Replace this with validator
  public CompletableFuture<Result<User>> getUserFailOnNotFound(String userId) {
    if(isNull(userId)) {
      return completedFuture(failedValidation("user is not found", "userId", userId));
    }

    return FetchSingleRecord.<User>forRecord("user")
      .using(usersStorageClient)
      .mapTo(User::new)
      .whenNotFound(failedValidation("user is not found", "userId", userId))
      .fetch(userId);
  }

  public CompletableFuture<Result<User>> getProxyUserByBarcode(String barcode) {
    //Not proxying, so no need to get proxy user
    if (StringUtils.isBlank(barcode)) {
      return completedFuture(succeeded(null));
    } else {
      return getUserByBarcode(barcode, "proxyUserBarcode");
    }
  }

  public CompletableFuture<Result<User>> getUserByBarcode(String barcode) {
    return getUserByBarcode(barcode, "userBarcode");
  }

  private CompletableFuture<Result<User>> getUserByBarcode(
    String barcode,
    String propertyName) {

    return CqlQuery.exactMatch("barcode", barcode)
      .after(query -> usersStorageClient.getMany(query, Limit.limit(1)))
      .thenApply(result -> result.next(this::mapResponseToUsers)
        .map(MultipleRecords::getRecords)
        .map(users -> users.stream().findFirst())
        .next(user -> user.map(Result::succeeded).orElseGet(() ->
          failedValidation("Could not find user with matching barcode",
            propertyName, barcode))));
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findUsersForRequests(
    MultipleRecords<Request> multipleRequests) {

    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> usersToFetch = requests.stream()
      .map(this::getUsersFromRequest)
      .flatMap(Collection::stream)
      .distinct()
      .collect(Collectors.toList());

    if (usersToFetch.isEmpty()) {
      return completedFuture(succeeded(multipleRequests));
    }

    final MultipleRecordFetcher<User> fetcher
      = new MultipleRecordFetcher<>(usersStorageClient, USERS_RECORD_PROPERTY, User::from);

    return fetcher.findByIds(usersToFetch)
      .thenApply(multipleUsersResult -> multipleUsersResult.next(
        multipleUsers -> of(() ->
          multipleRequests.mapRecords(request ->
            matchUsersToRequests(request, multipleUsers)))));
  }

  private ArrayList<String> getUsersFromRequest(Request request) {
    final ArrayList<String> usersToFetch = new ArrayList<>();

    if (request.getUserId() != null) {
      usersToFetch.add(request.getUserId());
    }

    if (request.getProxyUserId() != null) {
      usersToFetch.add(request.getProxyUserId());
    }

    return usersToFetch;
  }

  private Request matchUsersToRequests(
    Request request,
    MultipleRecords<User> users) {

    final Map<String, User> userMap = users.toMap(User::getId);

    return request
      .withRequester(userMap.getOrDefault(request.getUserId(), null))
      .withProxy(userMap.getOrDefault(request.getProxyUserId(), null));
  }

  private Result<MultipleRecords<User>> mapResponseToUsers(Response response) {
    return MultipleRecords.from(response, User::from, USERS_RECORD_PROPERTY);
  }
}
