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

import static org.folio.circulation.support.HttpResult.failure;
import static org.folio.circulation.support.HttpResult.success;
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
        onCreated.complete(success(
          loanAndRelatedRecords.withLoan(Loan.from(response.getJson(),
            loanAndRelatedRecords.getLoan().getItem()))));
      } else {
        onCreated.complete(failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return updateLoan(loanAndRelatedRecords.getLoan())
      .thenApply(r -> r.map(loanAndRelatedRecords::withLoan));
  }

  public CompletableFuture<HttpResult<Loan>> updateLoan(
    Loan loan) {

    CompletableFuture<HttpResult<Loan>> onUpdated = new CompletableFuture<>();

    JsonObject storageLoan = mapToStorageRepresentation(loan, loan.getItem());

    loansStorageClient.put(storageLoan.getString("id"), storageLoan, response -> {
      if (response.getStatusCode() == 204) {
        //TODO: Maybe refresh the representation from storage?
        onUpdated.complete(success(loan));
      } else {
        onUpdated.complete(failure(
          new ServerErrorFailure(String.format("Failed to update loan (%s:%s)",
            response.getStatusCode(), response.getBody()))));
      }
    });

    return onUpdated;
  }

  public CompletableFuture<HttpResult<Loan>> findOpenLoanByBarcode(FindByBarcodeQuery query) {
    return itemRepository.fetchByBarcode(query.getItemBarcode())
      .thenComposeAsync(itemResult -> itemResult.after(item -> findOpenLoans(item)
        .thenApply(loanResult -> loanResult.next(loans -> {
        final Optional<Loan> first = loans.getRecords().stream()
          .findFirst();

        if(loans.getTotalRecords() == 1 && first.isPresent()) {
          return success(Loan.from(first.get().asJson(), item));
        }
        else {
          return failure(new ServerErrorFailure(
            String.format("More than one open loan for item %s", query.getItemBarcode())));
        }
      }))))
      .thenComposeAsync(this::fetchUser)
      .thenApply(r -> r.next(loan -> refuseWhenDifferentUser(loan, query)));
  }

  private HttpResult<Loan> refuseWhenDifferentUser(
    Loan loan,
    FindByBarcodeQuery query) {

    if(query.userMatches(loan.getUser())) {
      return success(loan);
    }
    else {
      return failure(query.userDoesNotMatchError());
    }
  }

  public CompletableFuture<HttpResult<Loan>> getById(String id) {
    final Function<Response, HttpResult<Loan>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return success(Loan.from(response.getJson()));
      }
      else {
        return failure(new ForwardOnFailure(response));
      }
    };

    return loansStorageClient.get(id)
      .thenApply(mapResponse)
      .thenComposeAsync(this::fetchItem)
      .thenComposeAsync(this::fetchUser)
      .exceptionally(e -> failure(new ServerErrorFailure(e)));
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

  private CompletableFuture<HttpResult<MultipleRecords<Loan>>> fetchItems(
    HttpResult<MultipleRecords<Loan>> result) {

    return result.after(
      loans -> itemRepository.fetchFor(loans.getRecords().stream()
      .map(Loan::getItemId)
      .collect(Collectors.toList()))
      .thenApply(r -> r.map(items ->
        new MultipleRecords<>(loans.getRecords().stream().map(
          loan -> Loan.from(loan.asJson(),
            items.findRecordByItemId(loan.getItemId()))).collect(Collectors.toList()),
        loans.getTotalRecords()))));
  }

  public CompletableFuture<HttpResult<MultipleRecords<Loan>>> findBy(String query) {
    //TODO: Should fetch users for all loans
    return loansStorageClient.getMany(query)
      .thenApply(this::mapResponseToLoans)
      .thenComposeAsync(this::fetchItems);
  }

  private HttpResult<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() != 200) {
        return failure(new ServerErrorFailure(
          String.format("Failed to fetch loans from storage (%s:%s)",
            response.getStatusCode(), response.getBody())));
      }

      final MultipleRecordsWrapper wrappedLoans = fromBody(response.getBody(), "loans");

      if (wrappedLoans.isEmpty()) {
        return success(MultipleRecords.empty());
      }

      final MultipleRecords<Loan> mapped = new MultipleRecords<>(
        wrappedLoans.getRecords()
          .stream()
          .map(Loan::from)
          .collect(Collectors.toList()),
        wrappedLoans.getTotalRecords());

      return success(mapped);
    }
    else {
      log.warn("Did not receive response to request");
      return failure(new ServerErrorFailure(
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

  CompletableFuture<HttpResult<Boolean>> hasOpenLoan(String itemId) {
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
