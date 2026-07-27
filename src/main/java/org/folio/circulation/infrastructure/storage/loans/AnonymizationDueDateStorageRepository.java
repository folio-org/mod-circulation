package org.folio.circulation.infrastructure.storage.loans;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

import java.lang.invoke.MethodHandles;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Client for the {@code loan_anonymization_due} storage interface: the
 * {@code stamp}/{@code clear} maintainers and the {@code due}/{@code unevaluated}
 * finders. {@code stamp} is invoked only from the scheduled job; {@code clear}
 * from the invalidation hooks.
 */
public class AnonymizationDueDateStorageRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String RECORDS_PROPERTY_NAME = "loans";

  /** Loans per stamp request; a large page becomes several bounded upserts. */
  private static final int STAMP_CHUNK_SIZE = 2500;

  /** ISO-8601 millis-UTC wire form for due_at (parsed as timestamptz storage-side). */
  private static final DateTimeFormatter ISO_MILLIS_UTC =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

  /**
   * The wire form of one due instant. Storage re-validates this against a regex
   * and 422s the batch if it does not match, so the format must stay in sync
   * with {@code AnonymizationDueDateService.ISO_INSTANT}.
   */
  static String formatDueAt(ZonedDateTime dueAt) {
    return ISO_MILLIS_UTC.format(dueAt.withZoneSameInstant(ZoneOffset.UTC));
  }

  private final CollectionResourceClient stampClient;
  private final CollectionResourceClient clearClient;
  private final CollectionResourceClient dueClient;
  private final CollectionResourceClient unevaluatedClient;

  public AnonymizationDueDateStorageRepository(Clients clients) {
    this.stampClient = clients.anonymizationDueDateStampClient();
    this.clearClient = clients.anonymizationDueDateClearClient();
    this.dueClient = clients.anonymizationDueDateDueClient();
    this.unevaluatedClient = clients.anonymizationDueDateUnevaluatedClient();
  }

  /**
   * Upserts one due-date per loan; a null value records retain.
   *
   * @param dueDatesByLoanId loan id → due instant, or {@code null} for retain
   * @return the number of rows written
   */
  public CompletableFuture<Result<Integer>> stamp(Map<String, ZonedDateTime> dueDatesByLoanId) {
    if (dueDatesByLoanId.isEmpty()) {
      return completedFuture(succeeded(0));
    }

    final List<JsonObject> entries = dueDatesByLoanId.entrySet().stream()
      .map(e -> {
        final JsonObject entry = new JsonObject().put("loanId", e.getKey());
        // Omit dueAt to record retain; a present value is the due instant.
        if (e.getValue() != null) {
          entry.put("dueAt", formatDueAt(e.getValue()));
        }
        return entry;
      })
      .toList();

    if (entries.size() > STAMP_CHUNK_SIZE) {
      log.info("stamp:: stamping {} loans in chunks of {}", entries.size(), STAMP_CHUNK_SIZE);
    } else {
      log.info("stamp:: stamping {} loans", entries.size());
    }

    CompletableFuture<Result<Integer>> stamped = completedFuture(succeeded(0));
    for (int from = 0; from < entries.size(); from += STAMP_CHUNK_SIZE) {
      final List<JsonObject> chunk =
        entries.subList(from, Math.min(from + STAMP_CHUNK_SIZE, entries.size()));

      stamped = stamped.thenCompose(r -> r.after(soFar ->
        stampClient.post(new JsonObject().put("entries", new JsonArray(chunk)))
          .thenApply(updatedCountInterpreter()::flatMap)
          .thenApply(chunkResult -> chunkResult.map(updated -> soFar + updated))));
    }

    return stamped;
  }

  /** Invalidation: return specific loans to the unevaluated state (fee closure). */
  public CompletableFuture<Result<Integer>> clearByLoanIds(Collection<String> loanIds) {
    if (loanIds.isEmpty()) {
      return completedFuture(succeeded(0));
    }
    return clear(new JsonObject().put("loanIds", new JsonArray(List.copyOf(loanIds))));
  }

  /** Invalidation: return every loan to the unevaluated state (loan_history change). */
  public CompletableFuture<Result<Integer>> clearAll() {
    return clear(new JsonObject().put("all", true));
  }

  private CompletableFuture<Result<Integer>> clear(JsonObject body) {
    log.info("clear:: {}", body);
    return clearClient.post(body)
      .thenApply(updatedCountInterpreter()::flatMap);
  }

  /** Drain pass: closed loans whose due-date has arrived, oldest first. */
  public CompletableFuture<Result<MultipleRecords<Loan>>> findDue(int limit) {
    log.debug("findDue:: limit {}", limit);
    return dueClient.getManyWithRawQueryStringParameters("limit=" + limit)
      .thenApply(flatMapResult(r -> MultipleRecords.from(r, Loan::from, RECORDS_PROPERTY_NAME)));
  }

  /** Evaluation pass: closed loans with no due-date row yet. */
  public CompletableFuture<Result<MultipleRecords<Loan>>> findUnevaluated(int limit) {
    log.debug("findUnevaluated:: limit {}", limit);
    return unevaluatedClient.getManyWithRawQueryStringParameters("limit=" + limit)
      .thenApply(flatMapResult(r -> MultipleRecords.from(r, Loan::from, RECORDS_PROPERTY_NAME)));
  }

  /** Both maintainers answer {@code {"updated": n}} on 200; everything else fails. */
  private static ResponseInterpreter<Integer> updatedCountInterpreter() {
    return new ResponseInterpreter<Integer>()
      .flatMapOn(200, mapUsingJson(json -> json.getInteger("updated", 0)))
      .otherwise(forwardOnFailure());
  }
}
