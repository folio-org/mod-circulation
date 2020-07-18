package org.folio.circulation.infrastructure.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.notice.session.ExpiredSession;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;

public class PatronExpiredSessionRepository {
  private static final int EXPIRED_SESSIONS_LIMIT = 100;
  private static final String PATH_PARAM_WITH_QUERY = "expired-session-patron-ids?action_type=%s&session_inactivity_time_limit=%s&limit=%d";
  private static final String EXPIRED_SESSIONS = "expiredSessions";
  private final CollectionResourceClient patronExpiredSessionsStorageClient;

  public static PatronExpiredSessionRepository using(Clients clients) {
    return new PatronExpiredSessionRepository(clients.patronExpiredSessionsStorageClient());
  }

  private PatronExpiredSessionRepository(CollectionResourceClient patronExpiredSessionsStorageClient) {
    this.patronExpiredSessionsStorageClient = patronExpiredSessionsStorageClient;
  }

  public CompletableFuture<Result<List<ExpiredSession>>> findPatronExpiredSessions(
    PatronActionType actionType, String sessionInactivityTime) {

    return lookupExpiredSession(actionType.getRepresentation(), sessionInactivityTime)
      .thenApply(result -> result.next(Result::succeeded));
  }

  private CompletableFuture<Result<List<ExpiredSession>>> lookupExpiredSession(
    String actionType, String inactivityTimeLimit) {

    String path = String.format(PATH_PARAM_WITH_QUERY, actionType, inactivityTimeLimit, EXPIRED_SESSIONS_LIMIT);

    return FetchSingleRecord.<List<ExpiredSession>>forRecord("patronActionSessions")
      .using(patronExpiredSessionsStorageClient)
      .mapTo(this::mapFromJson)
      .fetch(path);
  }

  private List<ExpiredSession> mapFromJson(JsonObject json) {
    List<ExpiredSession> expiredSessions = new ArrayList<>();
    if (json.isEmpty() || json.getJsonArray(EXPIRED_SESSIONS).isEmpty()) {
      return expiredSessions;
    }

    JsonArray expiredSessionsArray = json.getJsonArray(EXPIRED_SESSIONS);
    for (int i = 0; i < expiredSessionsArray.size(); i++) {
      String patronId = expiredSessionsArray.getJsonObject(i)
        .getString("patronId", StringUtils.EMPTY);
      String actionType = expiredSessionsArray.getJsonObject(i)
        .getString("actionType", StringUtils.EMPTY);

      PatronActionType.from(actionType)
        .ifPresent(patronActionType -> expiredSessions.add(
          new ExpiredSession(patronId, patronActionType)));
    }
    return expiredSessions;
  }
}
