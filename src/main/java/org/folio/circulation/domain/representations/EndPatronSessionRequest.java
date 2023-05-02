package org.folio.circulation.domain.representations;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.utils.LogUtil.listAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EndPatronSessionRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String patronId;
  private final PatronActionType actionType;

  public static List<Result<EndPatronSessionRequest>> from(JsonObject jsonObject) {
    log.debug("from:: parameters jsonObject={}", jsonObject);

    final String END_SESSIONS = "endSessions";

    List<Result<EndPatronSessionRequest>> resultListOfEndPatronSessionRequests = new ArrayList<>();
    JsonArray endSessions = jsonObject.getJsonArray(END_SESSIONS);

    for (int i = 0; i < endSessions.size(); i++) {
      JsonObject endSession = endSessions.getJsonObject(i);

      final String patronIdFromJson = getProperty(endSession, PATRON_ID);

      if (isBlank(patronIdFromJson)) {
        log.warn("from:: patronIdFromJson for session object #{} is blank", i);
        return singletonList(failedValidation("End patron session request must have patron id",
          PATRON_ID, null));
      }

      String actionTypeRepresentation = getProperty(endSession, ACTION_TYPE);

      if (isBlank(actionTypeRepresentation)) {
        log.warn("from:: actionTypeRepresentation for session object #{} is blank", i);
        return singletonList(failedValidation("End patron session request must have action type",
          ACTION_TYPE, null));
      }

      resultListOfEndPatronSessionRequests.add(PatronActionType.from(actionTypeRepresentation)
        .map(patronActionType -> new EndPatronSessionRequest(patronIdFromJson, patronActionType))
        .map(Result::succeeded)
        .orElse(failedValidation("Invalid patron action type value", ACTION_TYPE, actionTypeRepresentation)));
    }
    log.info("from:: result {}", () -> listAsString(resultListOfEndPatronSessionRequests));
    return resultListOfEndPatronSessionRequests;
  }
}
