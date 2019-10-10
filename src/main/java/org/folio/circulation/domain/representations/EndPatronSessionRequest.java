package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class EndPatronSessionRequest {

  private final String patronId;
  private final PatronActionType actionType;
  private static final String PATRON_ID = "patronId";
  private static final String ACTION_TYPE = "actionType";

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

  public static Result<EndPatronSessionRequest> from(JsonObject json) {

    final String patronIdFromJson = getProperty(json, PATRON_ID);
    if (StringUtils.isBlank(patronIdFromJson)) {
      return failedValidation("End patron session request must have patron id",
        PATRON_ID, null);
    }

    String actionTypeRepresentation = getProperty(json, ACTION_TYPE);
    if (StringUtils.isBlank(actionTypeRepresentation)) {
      return failedValidation("End patron session request must have action type",
        ACTION_TYPE, null);
    }

    return PatronActionType.from(actionTypeRepresentation)
      .map(patronActionType -> new EndPatronSessionRequest(patronIdFromJson, patronActionType))
      .map(Result::succeeded)
      .orElse(failedValidation("Invalid patron action type value", ACTION_TYPE, actionTypeRepresentation));
  }
}
