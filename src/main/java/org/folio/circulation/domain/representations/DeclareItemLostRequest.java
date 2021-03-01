package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

@Getter
@AllArgsConstructor
public class DeclareItemLostRequest {
  private static final String DECLARED_LOST_DATETIME  = "declaredLostDateTime";
  private static final String SERVICE_POINT_ID = "servicePointId";

  private final String loanId;
  private final DateTime declaredLostDateTime;
  private final String comment;
  private final String servicePointId;

  public static Result<DeclareItemLostRequest> from(JsonObject json,
    String loanId) {
    final String comment = getProperty(json, "comment");

    String servicePointId = getProperty(json, "servicePointId");

    if(StringUtils.isBlank(servicePointId)) {
      return failedValidation("No service point data provided", SERVICE_POINT_ID, servicePointId);
    }

    final DateTime dateTime;
    try {
      dateTime = getDateTimeProperty(json, DECLARED_LOST_DATETIME);
    } catch (Exception e) {
        return failedValidation(
          e.getMessage(), DECLARED_LOST_DATETIME,
          getProperty(json, DECLARED_LOST_DATETIME));
      }

    return succeeded(new DeclareItemLostRequest(loanId, dateTime, comment,
      getProperty(json, "servicePointId")));
  }

  public String getServicePointId() {
    return this.servicePointId;
  }
}
