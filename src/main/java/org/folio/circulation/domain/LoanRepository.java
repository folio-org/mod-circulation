package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.MultipleRecordsWrapper.fromBody;

public class LoanRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient loansStorageClient;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;

  public LoanRepository(Clients clients) {
    loansStorageClient = clients.loansStorage();
    itemRepository = new ItemRepository(clients, true, true);
    userRepository = new UserRepository(clients);
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject storageLoan = mapToStorageRepresentation(
      loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getLoan().getItem());

    if(loanAndRelatedRecords.getLoanPolicy() != null) {
      storageLoan.put("loanPolicyId", loanAndRelatedRecords.getLoanPolicy().getId());
    }

    loansStorageClient.post(storageLoan, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(succeeded(
          loanAndRelatedRecords.withLoan(Loan.from(response.getJson(),
            loanAndRelatedRecords.getLoan().getItem()))));
      } else {
        onCreated.complete(failed(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
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

  public CompletableFuture<HttpResult<Loan>> findOpenLoanByBarcode(FindByBarcodeQuery query) {
    return itemRepository.fetchByBarcode(query.getItemBarcode())
      .thenComposeAsync(itemResult -> itemResult.after(item -> {
        if(item.isNotFound()) {
          return CompletableFuture.completedFuture(ValidationErrorFailure.failedResult(
            String.format("No item with barcode %s exists", query.getItemBarcode()),
            "itemBarcode", query.getItemBarcode()));
        }

        return findOpenLoans(item)
          .thenApply(loanResult -> loanResult.next(loans -> {
            final Optional<Loan> first = loans.getRecords().stream()
              .findFirst();

            if (loans.getTotalRecords() == 1 && first.isPresent()) {
              return succeeded(Loan.from(first.get().asJson(), item));
            } else {
              return failed(new ServerErrorFailure(
                String.format("More than one open loan for item %s", query.getItemBarcode())));
            }
          }));
      }))
      //TODO: Replace with fetch user by barcode to improve error message
      .thenComposeAsync(this::fetchUser)
      .thenApply(r -> r.next(loan -> refuseWhenDifferentUser(loan, query)));
  }

  public CompletableFuture<HttpResult<Loan>> findOpenLoanById(FindByIdQuery query) {
    return itemRepository.fetchById(query.getItemId())
      .thenComposeAsync(itemResult -> itemResult.after(item -> {
        if(item.isNotFound()) {
          return CompletableFuture.completedFuture(ValidationErrorFailure.failedResult(
            String.format("No item with ID %s exists", query.getItemId()),
            "itemId", query.getItemId()));
        }

        return findOpenLoans(item)
          .thenApply(loanResult -> loanResult.next(loans -> {
            final Optional<Loan> first = loans.getRecords().stream()
              .findFirst();

            if (loans.getTotalRecords() == 1 && first.isPresent()) {
              return succeeded(Loan.from(first.get().asJson(), item));
            } else {
              return failed(new ServerErrorFailure(
                String.format("More than one open loan for item %s", query.getItemId())));
            }
          }));
      }))
      .thenComposeAsync(this::fetchUser)
      .thenApply(r -> r.next(loan -> refuseWhenDifferentUser(loan, query)));
  }

  private HttpResult<Loan> refuseWhenDifferentUser(
    Loan loan,
    UserRelatedQuery query) {

    if(query.userMatches(loan.getUser())) {
      return succeeded(loan);
    }
    else {
      return failed(query.userDoesNotMatchError());
    }
  }

  public CompletableFuture<HttpResult<Loan>> getById(String id) {
    return fetchLoan(id)
      .thenComposeAsync(this::fetchItem)
      .thenComposeAsync(this::fetchUser)
      .exceptionally(e -> failed(new ServerErrorFailure(e)));
  }

  private CompletableFuture<HttpResult<Loan>> fetchLoan(String id) {
    return fetchLoan(id, null, null);
  }

  private CompletableFuture<HttpResult<Loan>> fetchLoan(
    String id,
    Item item,
    User user) {

    final Function<Response, HttpResult<Loan>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return succeeded(Loan.from(response.getJson(), item, user));
      }
      else {
        return failed(new ForwardOnFailure(response));
      }
    };

    return loansStorageClient.get(id)
      .thenApply(mapResponse);
  }

  private CompletableFuture<HttpResult<Loan>> fetchItem(HttpResult<Loan> result) {
    return result.after(loan ->
      itemRepository.fetchFor(loan)
      .thenApply(itemResult -> itemResult.map(
        item -> Loan.from(loan.asJson(), item))));
  }

  private CompletableFuture<HttpResult<Loan>> fetchUser(HttpResult<Loan> result) {
    return result.after(loan ->
      userRepository.getUser(loan)
        .thenApply(userResult -> userResult.map(
          user -> Loan.from(loan.asJson(), loan.getItem(), user))));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Loan>>> findBy(String query) {
    //TODO: Should fetch users for all loans
    return loansStorageClient.getMany(query)
      .thenApply(this::mapResponseToLoans)
      .thenComposeAsync(loans -> itemRepository.fetchItemsFor(loans, Loan::withItem));
  }

  private HttpResult<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() != 200) {
        return failed(new ServerErrorFailure(
          String.format("Failed to fetch loans from storage (%s:%s)",
            response.getStatusCode(), response.getBody())));
      }

      final MultipleRecordsWrapper wrappedLoans = fromBody(response.getBody(), "loans");

      if (wrappedLoans.isEmpty()) {
        return succeeded(MultipleRecords.empty());
      }

      final MultipleRecords<Loan> mapped = new MultipleRecords<>(
        wrappedLoans.getRecords()
          .stream()
          .map(Loan::from)
          .collect(Collectors.toList()),
        wrappedLoans.getTotalRecords());

      return succeeded(mapped);
    }
    else {
      log.warn("Did not receive response to request");
      return failed(new ServerErrorFailure(
        "Did not receive response to request for multiple loans"));
    }
  }

  private static JsonObject mapToStorageRepresentation(Loan loan, Item item) {
    JsonObject storageLoan = loan.asJson();

    storageLoan.remove("metadata");
    storageLoan.remove("item");
    storageLoan.remove("itemStatus");
    storageLoan.put("itemStatus", item.getStatus());

    return storageLoan;
  }

  public CompletableFuture<HttpResult<Boolean>> hasOpenLoan(String itemId) {
    return findOpenLoans(itemId)
      .thenApply(r -> r.map(loans -> !loans.getRecords().isEmpty()));
  }

  private CompletableFuture<HttpResult<MultipleRecords<Loan>>> findOpenLoans(Item item) {
    return findOpenLoans(item.getItemId());
  }

  private CompletableFuture<HttpResult<MultipleRecords<Loan>>> findOpenLoans(String itemId) {
    final String openLoans = String.format(
      "itemId==%s and status.name==\"%s\"", itemId, "Open");

    return CqlHelper.encodeQuery(openLoans).after(query ->
      loansStorageClient.getMany(query, 1, 0)
        .thenApply(this::mapResponseToLoans));
  }
}
