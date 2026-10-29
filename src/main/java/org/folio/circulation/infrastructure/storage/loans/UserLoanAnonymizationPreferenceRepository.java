package org.folio.circulation.infrastructure.storage.loans;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.anonymization.UserLoanAnonymizationPreference;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The copy of the users' loan anonymization preferences in mod-circulation-storage.
 */
public class UserLoanAnonymizationPreferenceRepository {
  private final CollectionResourceClient preferencesStorageClient;

  public UserLoanAnonymizationPreferenceRepository(Clients clients) {
    preferencesStorageClient = clients.userLoanAnonymizationPreferencesStorageClient();
  }

  /**
   * Stores exactly the given preferences, the rows of all other users are removed.
   */
  public CompletableFuture<Result<Void>> replaceAll(
    List<UserLoanAnonymizationPreference> preferences) {

    JsonArray entries = new JsonArray();
    preferences.forEach(preference -> entries.add(new JsonObject()
      .put("userId", preference.userId())
      .put("preference", preference.preference())));

    JsonObject body = new JsonObject().put("preferences", entries);

    final ResponseInterpreter<Void> interpreter = new ResponseInterpreter<Void>()
      .on(204, succeeded(null))
      .otherwise(forwardOnFailure());

    return preferencesStorageClient.put(body).thenApply(interpreter::flatMap);
  }
}
