package api.support.fakes.processors;

import static api.support.fakes.storage.Storage.getStorage;
import static api.support.http.InterfaceUrls.loanHistoryStorageUrl;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ClockManager.getClockManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;

public final class LoanHistoryProcessor {
  private static boolean loanHistoryEnabled = false;

  public static CompletableFuture<JsonObject> persistLoanHistory(
    JsonObject oldLoan, JsonObject newLoan) {

    if (!loanHistoryEnabled) {
      return completedFuture(newLoan);
    }

    final String operation = oldLoan == null ? "I" : "U";

    final String id = UUID.randomUUID().toString();
    final JsonObject historyRecord = new JsonObject()
      .put("id", id)
      .put("operation", operation)
      .put("createdDate", getClockManager().getDateTime().toString())
      .put("loan", newLoan);

    getStorage().getTenantResources(loanHistoryStorageUrl(""))
      .put(id, historyRecord);

    return completedFuture(newLoan);
  }

  // Loan history is disabled by default for performance reason
  public static void setLoanHistoryEnabled(boolean enable) {
    loanHistoryEnabled = enable;
  }
}
