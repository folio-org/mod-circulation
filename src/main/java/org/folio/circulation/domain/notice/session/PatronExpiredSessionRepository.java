package org.folio.circulation.domain.notice.session;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;

public class PatronExpiredSessionRepository {

  private static final int SESSION_LIMIT = 1;
  private static final String PATH_PARAM_WITH_QUERY = "expired-session-patron-ids?action_type=%s&session_inactivity_time_limit=%s&limit=%d";
  private static final String EXPIRED_SESSIONS = "expiredSessions";
  private final CollectionResourceClient patronExpiredSessionsStorageClient;

  public static PatronExpiredSessionRepository using(Clients clients) {
    return new PatronExpiredSessionRepository(clients.patronExpiredSessionsStorageClient());
  }

  private PatronExpiredSessionRepository(CollectionResourceClient patronExpiredSessionsStorageClient) {
    this.patronExpiredSessionsStorageClient = patronExpiredSessionsStorageClient;
  }

  public CompletableFuture<Result<ExpiredSession>> findPatronExpiredSessions(PatronActionType actionType,
                                                                     String sessionInactivityTime) {
    return lookupExpiredSession(actionType.getRepresentation(), sessionInactivityTime)
      .thenApply(result -> result.next(Result::succeeded));
  }

  private CompletableFuture<Result<ExpiredSession>> lookupExpiredSession(String actionType,
                                                                 String inactivityTimeLimit) {
    String path = String.format(PATH_PARAM_WITH_QUERY, actionType, inactivityTimeLimit, SESSION_LIMIT);
    return FetchSingleRecord.<ExpiredSession>forRecord("patronActionSessions")
      .using(patronExpiredSessionsStorageClient)
      .mapTo(this::mapFromJson)
      .fetch(path);
  }

  private ExpiredSession mapFromJson(JsonObject json) {
    if (json.isEmpty() || json.getJsonArray(EXPIRED_SESSIONS).isEmpty()) {
      return new ExpiredSession();
    }

    JsonObject jsonObject = json.getJsonArray(EXPIRED_SESSIONS)
      .getJsonObject(0);

    String patronId = jsonObject.getString("patronId", StringUtils.EMPTY);
    String actionType = jsonObject.getString("actionType", StringUtils.EMPTY);

    return PatronActionType.from(actionType)
      .map(patronActionType -> new ExpiredSession(patronId, patronActionType))
      .orElse(new ExpiredSession());
  }
}
