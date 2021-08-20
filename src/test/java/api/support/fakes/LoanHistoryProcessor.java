package api.support.fakes;

import static api.support.APITestContext.getTenantId;
import static api.support.fakes.Storage.getStorage;
import static api.support.http.InterfaceUrls.loanHistoryStorageUrl;

import java.util.Map;
import java.util.UUID;

import org.folio.circulation.support.ClockManager;

import io.vertx.core.json.JsonObject;

public final class LoanHistoryProcessor {
  private static boolean loanHistoryEnabled = false;

  public static JsonObject persistLoanHistory(JsonObject oldLoan, JsonObject newLoan) {
    if (!loanHistoryEnabled) {
      return newLoan;
    }

    final String operation = oldLoan == null ? "I" : "U";

    final String id = UUID.randomUUID().toString();
    final JsonObject historyRecord = new JsonObject()
      .put("id", id)
      .put("operation", operation)
      .put("createdDate", ClockManager.getDateTime().toString())
      .put("loan", newLoan);

    getLoanHistoryStorage().put(id, historyRecord);

    return newLoan;
  }

  private static Map<String, JsonObject> getLoanHistoryStorage() {
    return getStorage()
      .getTenantResources(loanHistoryStorageUrl("").getPath(), getTenantId());
  }

  // Loan history is disabled by default for performance reason
  public static void setLoanHistoryEnabled(boolean enable) {
    loanHistoryEnabled = enable;
  }
}
