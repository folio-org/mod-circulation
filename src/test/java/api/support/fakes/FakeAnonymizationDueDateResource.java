package api.support.fakes;

import static api.support.APITestContext.createWebClient;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.http.InterfaceUrls;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Fake for the {@code loan_anonymization_due} storage interface, holding the
 * due-date in an in-memory map and serving the four operations:
 * <ul>
 *   <li>{@code POST /stamp} — upsert a row for each closed loan with a userId
 *   ({@code dueAt} omitted = retain);</li>
 *   <li>{@code POST /clear} — delete rows by loanIds or all;</li>
 *   <li>{@code GET /due} — closed+userId loans whose row is present and due;</li>
 *   <li>{@code GET /unevaluated} — closed+userId loans with no row yet.</li>
 * </ul>
 *
 * <p>The map is static so all four handlers share it, and is keyed by loan id;
 * the finders only iterate loans that currently exist in fake storage.</p>
 */
public class FakeAnonymizationDueDateResource implements Handler<RoutingContext> {

  // loanId -> due instant; Optional.empty() = never. Key present = evaluated.
  private static final ConcurrentMap<String, Optional<ZonedDateTime>> SIDE_TABLE =
    new ConcurrentHashMap<>();

  private enum Mode { STAMP, CLEAR, DUE, UNEVALUATED }

  private final Mode mode;

  private FakeAnonymizationDueDateResource(Mode mode) {
    this.mode = mode;
  }

  public static FakeAnonymizationDueDateResource stampResource() {
    return new FakeAnonymizationDueDateResource(Mode.STAMP);
  }

  public static FakeAnonymizationDueDateResource clearResource() {
    return new FakeAnonymizationDueDateResource(Mode.CLEAR);
  }

  public static FakeAnonymizationDueDateResource dueResource() {
    return new FakeAnonymizationDueDateResource(Mode.DUE);
  }

  public static FakeAnonymizationDueDateResource unevaluatedResource() {
    return new FakeAnonymizationDueDateResource(Mode.UNEVALUATED);
  }

  @Override
  public void handle(RoutingContext routingContext) {
    if (mode == Mode.DUE || mode == Mode.UNEVALUATED) {
      findLoans(mode).thenAccept(loans -> routingContext.response()
        .putHeader("Content-type", "application/json")
        .setStatusCode(200)
        .end(new JsonObject().put("loans", loans).put("totalRecords", loans.size()).encode()));
      return;
    }

    routingContext.request().bodyHandler(body -> {
      final JsonObject requestJson = body.toJsonObject();
      final CompletableFuture<Integer> updated = mode == Mode.STAMP
        ? stampLoans(requestJson)
        : clearLoans(requestJson);

      updated.thenAccept(count -> routingContext.response()
        .putHeader("Content-type", "application/json")
        .setStatusCode(200)
        .end(new JsonObject().put("updated", count).encode()));
    });
  }

  private CompletableFuture<Integer> stampLoans(JsonObject requestJson) {
    CompletableFuture<Integer> result = completedFuture(0);

    for (Object entryObject : requestJson.getJsonArray("entries", new JsonArray())) {
      final JsonObject entry = (JsonObject) entryObject;
      final String loanId = entry.getString("loanId");
      final String dueAt = entry.getString("dueAt"); // absent = never

      result = result.thenCompose(count -> getLoanById(loanId).thenApply(loan -> {
        final JsonObject json = loan.getJson();
        // Real guard: only closed loans that still carry a userId.
        if (!"Closed".equals(json.getJsonObject("status", new JsonObject()).getString("name"))
          || json.getString("userId") == null) {
          return count;
        }
        SIDE_TABLE.put(loanId,
          dueAt == null ? Optional.empty() : Optional.of(ZonedDateTime.parse(dueAt)));
        return count + 1;
      }));
    }
    return result;
  }

  private CompletableFuture<Integer> clearLoans(JsonObject requestJson) {
    if (Boolean.TRUE.equals(requestJson.getBoolean("all"))) {
      final int cleared = SIDE_TABLE.size();
      SIDE_TABLE.clear();
      return completedFuture(cleared);
    }
    int cleared = 0;
    for (Object id : requestJson.getJsonArray("loanIds", new JsonArray())) {
      if (SIDE_TABLE.remove(id.toString()) != null) {
        cleared++;
      }
    }
    return completedFuture(cleared);
  }

  private CompletableFuture<JsonArray> findLoans(Mode finder) {
    final ZonedDateTime now = ClockUtil.getZonedDateTime();
    return closedLoansWithUser().thenApply(loans -> {
      final JsonArray result = new JsonArray();
      loans.forEach(o -> {
        final JsonObject loan = (JsonObject) o;
        final Optional<ZonedDateTime> row = SIDE_TABLE.get(loan.getString("id"));
        final boolean present = SIDE_TABLE.containsKey(loan.getString("id"));
        if ((finder == Mode.UNEVALUATED && !present)
          || (finder == Mode.DUE && present && row.isPresent() && !now.isBefore(row.get()))) {
          result.add(loan);
        }
      });
      return result;
    });
  }

  private CompletableFuture<JsonArray> closedLoansWithUser() {
    final String query = URLEncoder.encode("status.name==\"Closed\" and userId=\"\"",
      StandardCharsets.UTF_8);
    return createWebClient()
      .get(InterfaceUrls.loansStorageUrl("") + "?limit=10000&query=" + query)
      .thenApply(Result::value)
      .thenApply(response -> response.getJson().getJsonArray("loans", new JsonArray()));
  }

  private CompletableFuture<Response> getLoanById(String id) {
    return createWebClient().get(InterfaceUrls.loansStorageUrl("/" + id))
      .thenApply(Result::value);
  }
}
