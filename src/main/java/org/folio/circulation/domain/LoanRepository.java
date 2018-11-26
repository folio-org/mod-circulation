package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.of;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.circulation.domain.validation.UserNotFoundValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class LoanRepository {
  private final CollectionResourceClient loansStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public LoanRepository(Clients clients) {
    loansStorageClient = clients.loansStorage();
    itemRepository = new ItemRepository(clients, true, true);
    userRepository = new UserRepository(clients);
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    JsonObject storageLoan = mapToStorageRepresentation(
      loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getLoan().getItem());

    if(loanAndRelatedRecords.getLoanPolicy() != null) {
      storageLoan.put("loanPolicyId", loanAndRelatedRecords.getLoanPolicy().getId());
    }

    return loansStorageClient.post(storageLoan).thenApply(response -> {
      if (response.getStatusCode() == 201) {
        return succeeded(
          loanAndRelatedRecords.withLoan(Loan.from(response.getJson(),
            loanAndRelatedRecords.getLoan().getItem())));
      } else {
        return failed(new ForwardOnFailure(response));
      }
    });
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return updateLoan(loanAndRelatedRecords.getLoan())
      .thenApply(r -> r.map(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<HttpResult<Loan>> updateLoan(Loan loan) {
    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    final Function<Response, HttpResult<Loan>> mapResponse = response -> {
      if (response.getStatusCode() == 204) {
        return succeeded(loan);
      } else {
        return failed(
          new ServerErrorFailure(String.format("Failed to update loan (%s:%s)",
            response.getStatusCode(), response.getBody())));
      }
    };

    return loansStorageClient.put(loan.getId(), storageLoan)
      .thenApply(mapResponse)
      .thenComposeAsync(r -> r.after(
        //Fetch updated loan without having to get the item and the user again
        l -> fetchLoan(l.getId(), loan.getItem(), loan.getUser())));
  }

  //TODO: Extract to separate class rather than repository
  public CompletableFuture<HttpResult<Loan>> findOpenLoanById(String itemId) {

    final UserNotFoundValidator userNotFoundValidator = new UserNotFoundValidator(
      userId -> failure("user is not found", "userId", userId));

    return itemRepository.fetchById(itemId)
      .thenComposeAsync(itemResult -> itemResult.after(item -> {
        if(item.isNotFound()) {
          return completedFuture(ValidationErrorFailure.failedResult(
            String.format("No item with ID %s exists", itemId),
            "itemId", itemId));
        }

        return findOpenLoans(item)
          .thenApply(loanResult -> loanResult.next(loans -> {
            final Optional<Loan> first = loans.getRecords().stream()
              .findFirst();

            if (loans.getTotalRecords() == 1 && first.isPresent()) {
              return succeeded(Loan.from(first.get().asJson(), item));
            } else {
              return failed(new ServerErrorFailure(
                String.format("More than one open loan for item %s", itemId)));
            }
          }));
      }))
      .thenComposeAsync(this::fetchUser)
      .thenApply(userNotFoundValidator::refuseWhenUserNotFound);
  }

  /**
   *
   * @param request the request to fetch the open loan for the same item for
   * @return  success with loan if one found,
   * success with null if the no open loan is found,
   * failure if more than one open loan for the item found
   */
  CompletableFuture<HttpResult<Loan>> findOpenLoanForRequest(Request request) {
    return findOpenLoans(request.getItemId())
      .thenApply(loansResult -> loansResult.next(loans -> {
        //TODO: Consider introducing an unknown loan class, instead of null
        if (loans.getTotalRecords() == 0) {
          return succeeded(null);
        }
        else if(loans.getTotalRecords() == 1) {
          final Optional<Loan> firstLoan = loans.getRecords().stream().findFirst();

          return firstLoan
            .map(loan -> succeeded(Loan.from(loan.asJson(), request.getItem())))
            .orElse(null);
        } else {
          return failed(new ServerErrorFailure(
            String.format("More than one open loan for item %s", request.getItemId())));
        }
      }));
  }

  public CompletableFuture<HttpResult<Loan>> getById(String id) {
    return fetchLoan(id)
      .thenComposeAsync(this::fetchItem)
      .thenComposeAsync(this::fetchUser)
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }

  private CompletableFuture<HttpResult<Loan>> fetchLoan(String id) {
    return new SingleRecordFetcher<>(
      loansStorageClient, "loan", Loan::from)
      .fetch(id);
  }

  private CompletableFuture<HttpResult<Loan>> fetchLoan(
    String id,
    Item item,
    User user) {

    return new SingleRecordFetcher<>(
      loansStorageClient, "loan", representation -> Loan.from(representation, item, user, null))
      .fetch(id);
  }

  private CompletableFuture<HttpResult<Loan>> fetchItem(HttpResult<Loan> result) {
    return result.combineAfter(itemRepository::fetchFor, Loan::withItem);
  }

  //TODO: Check if user not found should result in failure?
  private CompletableFuture<HttpResult<Loan>> fetchUser(HttpResult<Loan> result) {
    return result.combineAfter(userRepository::getUser,
      (loan, user) -> Loan.from(loan.asJson(), loan.getItem(), user, null));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Loan>>> findBy(String query) {
    //TODO: Should fetch users for all loans
    return loansStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(this::mapResponseToLoans)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  private HttpResult<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    return MultipleRecords.from(response, Loan::from, "loans");
  }
  
  

  private static JsonObject mapToStorageRepresentation(Loan loan, Item item) {
    JsonObject storageLoan = loan.asJson();

    storageLoan.remove("metadata");
    storageLoan.remove("item");
    storageLoan.remove("itemStatus");

    //TODO: Check for null item status
    storageLoan.put("itemStatus", item.getStatus().getValue());

    return storageLoan;
  }

  public CompletableFuture<HttpResult<Boolean>> hasOpenLoan(String itemId) {
    return findOpenLoans(itemId)
      .thenApply(r -> r.map(loans -> !loans.getRecords().isEmpty()));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Loan>>> findOpenLoans(Item item) {
    return findOpenLoans(item.getItemId());
  }

  private CompletableFuture<HttpResult<MultipleRecords<Loan>>> findOpenLoans(String itemId) {
    final String openLoans = String.format(
      "itemId==%s and status.name==\"%s\"", itemId, "Open");
    log.info("Querying open loan with query {}", openLoans);

    return CqlHelper.encodeQuery(openLoans).after(query ->
      loansStorageClient.getMany(query, 1, 0)
        .thenApply(this::mapResponseToLoans));
  }

  CompletableFuture<HttpResult<MultipleRecords<Request>>> findOpenLoansFor(
    MultipleRecords<Request> multipleRequests) {

    //TODO: Need to handle multiple open loans for same item (with failure?)

    Collection<Request> requests = multipleRequests.getRecords();

    //TODO: Extract this repeated getting of a collection of property values
    final List<String> itemsToFetchLoansFor = requests.stream()
      .filter(Objects::nonNull)
      .map(Request::getItemId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());

    if(itemsToFetchLoansFor.isEmpty()) {
      log.info("No items to search for current loans for");
      return completedFuture(succeeded(multipleRequests));
    }

    HttpResult<String> queryResult = CqlHelper.multipleRecordsCqlQuery(
      String.format("status.name==\"%s\" and ", "Open"),
      "itemId", itemsToFetchLoansFor);

    return queryResult.after(query -> loansStorageClient.getMany(query, requests.size(), 0)
        .thenApply(this::mapResponseToLoans)
      .thenApply(multipleLoansResult -> multipleLoansResult.next(
        loans -> matchLoansToRequests(multipleRequests, loans))));
  }

  private HttpResult<MultipleRecords<Request>> matchLoansToRequests(
    MultipleRecords<Request> requests,
    MultipleRecords<Loan> loans) {

    return of(() ->
      requests.mapRecords(request -> matchLoansToRequest(request, loans)));
  }

  private Request matchLoansToRequest(
    Request request,
    MultipleRecords<Loan> loans) {

    final Map<String, Loan> loanMap = loans.toMap(Loan::getItemId);

    return request
      .withLoan(loanMap.getOrDefault(request.getItemId(), null));
  }

}
