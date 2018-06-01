package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

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
    final String cqlQuery;

    try {
      String unencodedQuery = String.format(
        "itemId==%s and status.name==\"%s\"", itemId, "Open");

      log.info("Finding open loans with {}", unencodedQuery);

      cqlQuery = URLEncoder.encode(unencodedQuery,
        String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      return completedFuture(HttpResult.failure(
        new ServerErrorFailure("Failed to encode CQL query for finding open loans")));
    }

    CompletableFuture<HttpResult<Collection<JsonObject>>> loansFetched = new CompletableFuture<>();

    loansStorageClient.getMany(cqlQuery, 1, 0, fetchLoansResponse -> {
      if (fetchLoansResponse.getStatusCode() == 200) {
        final JsonArray foundLoans = fetchLoansResponse.getJson().getJsonArray("loans");

        log.info("Found open loans: {}", foundLoans.encodePrettily());

        loansFetched.complete(HttpResult.success(JsonArrayHelper.toList(foundLoans)));
      } else {
        loansFetched.complete(HttpResult.failure(new ServerErrorFailure(
          String.format("Failed to fetch open loans: %s: %s",
            fetchLoansResponse.getStatusCode(), fetchLoansResponse.getBody()))));
      }
    });

    return loansFetched
      .thenApply(r -> r.next(items -> HttpResult.success(!items.isEmpty())));
  }
}
