package org.folio.circulation.infrastructure.storage.sessions;

import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ACTION_SESSIONS;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.session.ExpiredSession;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PatronExpiredSessionRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
    PatronActionType actionType, ZonedDateTime sessionInactivityTime) {

    String path = String.format(PATH_PARAM_WITH_QUERY, actionType.getRepresentation(),
      sessionInactivityTime.toString(), EXPIRED_SESSIONS_LIMIT);

    return FetchSingleRecord.<List<ExpiredSession>>forRecord(PATRON_ACTION_SESSIONS)
      .using(patronExpiredSessionsStorageClient)
      .mapTo(this::mapFromJson)
      .fetch(path);
  }

  private List<ExpiredSession> mapFromJson(JsonObject json) {
    log.debug("mapFromJson:: processing expired sessions from response");
    List<ExpiredSession> expiredSessions = new ArrayList<>();
    if (json.isEmpty() || json.getJsonArray(EXPIRED_SESSIONS).isEmpty()) {
      log.info("mapFromJson:: no expired sessions found in response");
      return expiredSessions;
    }

    JsonArray expiredSessionsArray = json.getJsonArray(EXPIRED_SESSIONS);
    for (int i = 0; i < expiredSessionsArray.size(); i++) {
      String patronId = expiredSessionsArray.getJsonObject(i)
        .getString(PATRON_ID, StringUtils.EMPTY);
      String actionType = expiredSessionsArray.getJsonObject(i)
        .getString(ACTION_TYPE, StringUtils.EMPTY);

      PatronActionType.from(actionType)
        .ifPresent(patronActionType -> expiredSessions.add(
          new ExpiredSession(patronId, patronActionType)));
    }
    log.info("mapFromJson:: mapped {} expired sessions", expiredSessions.size());
    return expiredSessions;
  }
}
