package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.folio.circulation.support.MultipleRecordsWrapper.fromBody;

public class LoanRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient loansStorageClient;
  private final ItemRepository itemRepository;

  public LoanRepository(Clients clients) {
    loansStorageClient = clients.loansStorage();
    itemRepository = new ItemRepository(clients, true, true);
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> createLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCreated = new CompletableFuture<>();

    JsonObject storageLoan = convertLoanToStorageRepresentation(
      loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getLoan().getItem());

    if(loanAndRelatedRecords.getLoanPolicy() != null) {
      storageLoan.put("loanPolicyId", loanAndRelatedRecords.getLoanPolicy().getId());
    }

    loansStorageClient.post(storageLoan, response -> {
      if (response.getStatusCode() == 201) {
        onCreated.complete(HttpResult.success(
          loanAndRelatedRecords.withLoan(Loan.from(response.getJson(),
            loanAndRelatedRecords.getLoan().getItem()))));
      } else {
        onCreated.complete(HttpResult.failure(new ForwardOnFailure(response)));
      }
    });

    return onCreated;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> updateLoan(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> onUpdated = new CompletableFuture<>();

    JsonObject storageLoan = convertLoanToStorageRepresentation(
      loanAndRelatedRecords.getLoan(), loanAndRelatedRecords.getLoan().getItem());

    loansStorageClient.put(storageLoan.getString("id"), storageLoan, response -> {
      if (response.getStatusCode() == 204) {
        onUpdated.complete(HttpResult.success(loanAndRelatedRecords));
      } else {
        onUpdated.complete(HttpResult.failure(new ServerErrorFailure("Failed to update loan")));
      }
    });

    return onUpdated;
  }

  public CompletableFuture<HttpResult<Loan>> getById(String id) {
    CompletableFuture<Response> getLoanCompleted = new CompletableFuture<>();

    loansStorageClient.get(id, getLoanCompleted::complete);

    final Function<Response, HttpResult<Loan>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(Loan.from(response.getJson()));
      }
      else {
        return HttpResult.failure(new ForwardOnFailure(response));
      }
    };

    return getLoanCompleted
      .thenApply(mapResponse)
      .thenComposeAsync(this::fetchItem)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private CompletableFuture<HttpResult<Loan>> fetchItem(
    HttpResult<Loan> result) {

    return result.after(loan ->
      itemRepository.fetchFor(loan)
      .thenApply(itemResult -> itemResult.map(
        item -> Loan.from(loan.asJson(), item))));
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
    CompletableFuture<Response> responseReceived = new CompletableFuture<>();

    loansStorageClient.getMany(query, responseReceived::complete);

    return responseReceived
      .thenApply(this::mapResponseToLoans)
      .thenComposeAsync(this::fetchItems);
  }

  private HttpResult<MultipleRecords<Loan>> mapResponseToLoans(Response response) {
      if (response.getStatusCode() != 200) {
        return HttpResult.failure(new ServerErrorFailure("Failed to fetch loans from storage"));
      }

      final MultipleRecordsWrapper wrappedLoans = fromBody(response.getBody(), "loans");

      if (wrappedLoans.isEmpty()) {
        return HttpResult.success(MultipleRecords.empty());
      }

      final MultipleRecords<Loan> mapped = new MultipleRecords<>(
        wrappedLoans.getRecords()
          .stream()
          .map(Loan::from)
          .collect(Collectors.toList()),
        wrappedLoans.getTotalRecords());

      return HttpResult.success(mapped);
  }

  private static JsonObject convertLoanToStorageRepresentation(
    Loan loan,
    Item item) {

    JsonObject storageLoan = loan.asJson();

    storageLoan.remove("item");
    storageLoan.remove("itemStatus");
    storageLoan.put("itemStatus", item.getStatus());

    return storageLoan;
  }

  CompletableFuture<HttpResult<Boolean>> hasOpenLoan(String itemId) {
    final String openLoans = String.format(
        "itemId==%s and status.name==\"%s\"", itemId, "Open");

    return CqlHelper.encodeQuery(openLoans).after(query -> {
      final CompletableFuture<Response> fetched = new CompletableFuture<>();

      loansStorageClient.getMany(query, 1, 0, fetched::complete);

      return fetched
        .thenApply(this::mapResponseToLoans)
        .thenApply(r -> r.map(loans -> !loans.getRecords().isEmpty()));
    });
  }
}
