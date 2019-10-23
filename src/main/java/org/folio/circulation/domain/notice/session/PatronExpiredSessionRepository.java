package org.folio.circulation.domain.notice.session;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;

import io.vertx.core.json.JsonObject;

public class PatronExpiredSessionRepository {

  private static final int PAGE_LIMIT = 1;
  private static final String PATH_PARAM_WITH_QUERY = "expired-session-patron-ids?action_type=%s&session_inactivity_time_limit=%s&limit=%d";
  private static final String EXPIRED_SESSIONS = "expiredSessions";
  private final CollectionResourceClient patronExpiredSessionsStorageClient;

  public static PatronExpiredSessionRepository using(Clients clients) {
    return new PatronExpiredSessionRepository(clients.patronExpiredSessionsStorageClient());
  }

  private PatronExpiredSessionRepository(CollectionResourceClient patronExpiredSessionsStorageClient) {
    this.patronExpiredSessionsStorageClient = patronExpiredSessionsStorageClient;
  }

  public CompletableFuture<Result<String>> findPatronExpiredSessions(PatronActionType actionType,
                                                                     String sessionInactivityTime) {

    return lookupExpiredSession(actionType.getRepresentation(), sessionInactivityTime)
      .thenApply(result -> result.next(patronId -> StringUtils.isNotBlank(patronId)
        ? Result.succeeded(patronId)
        : Result.failed(new ServerErrorFailure("PatronId was not found"))));
  }

  private CompletableFuture<Result<String>> lookupExpiredSession(String actionType, String inactivityTimeLimit) {

    String path = String.format(PATH_PARAM_WITH_QUERY, actionType, inactivityTimeLimit, PAGE_LIMIT);

    return FetchSingleRecord.<String>forRecord("patronActionSessions")
      .using(patronExpiredSessionsStorageClient)
      .mapTo(this::mapFromJson)
      .whenNotFound(failedValidation(
        new ValidationError("Expired session was not found", Collections.emptyMap())))
      .fetch(path);
  }

  private String mapFromJson(JsonObject json) {
    if (json.isEmpty() || json.getJsonArray(EXPIRED_SESSIONS).isEmpty()) {
      return StringUtils.EMPTY;
    }
    return json.getJsonArray(EXPIRED_SESSIONS)
      .getJsonObject(0)
      .getString("patronId", StringUtils.EMPTY);
  }
}
