package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.Result;

public class EndPatronSessionRequest {

  private final PatronActionType actionType;
  private static final String PATRON_ID = "patronId";
  private static final String ACTION_TYPE = "actionType";
  private static final String END_SESSIONS = "endSessions";
  private final String patronId;

  public EndPatronSessionRequest(String patronId, PatronActionType actionType) {
    this.patronId = patronId;
    this.actionType = actionType;
  }

  public String getPatronId() {
    return patronId;
  }

  public PatronActionType getActionType() {
    return actionType;
  }

  public static List<Result<EndPatronSessionRequest>> from(JsonObject jsonObject) {

    List<Result<EndPatronSessionRequest>> resultListOfEndPatronSessionRequests = new ArrayList<>();
    JsonArray endSessions = jsonObject.getJsonArray(END_SESSIONS);

    for (int i = 0; i < endSessions.size(); i++) {
      JsonObject endSession = endSessions.getJsonObject(i);

      final String patronIdFromJson = getProperty(endSession, PATRON_ID);
      if (StringUtils.isBlank(patronIdFromJson)) {
        return Collections.singletonList(failedValidation("End patron session request must have patron id",
          PATRON_ID, null));
      }

      String actionTypeRepresentation = getProperty(endSession, ACTION_TYPE);
      if (StringUtils.isBlank(actionTypeRepresentation)) {
        return Collections.singletonList(failedValidation("End patron session request must have action type",
          ACTION_TYPE, null));
      }

      resultListOfEndPatronSessionRequests.add(PatronActionType.from(actionTypeRepresentation)
        .map(patronActionType -> new EndPatronSessionRequest(patronIdFromJson, patronActionType))
        .map(Result::succeeded)
        .orElse(failedValidation("Invalid patron action type value", ACTION_TYPE, actionTypeRepresentation)));
    }
    return resultListOfEndPatronSessionRequests;
  }
}
