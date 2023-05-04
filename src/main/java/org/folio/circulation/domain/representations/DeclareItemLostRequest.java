package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class DeclareItemLostRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String DECLARED_LOST_DATETIME  = "declaredLostDateTime";
  private static final String SERVICE_POINT_ID = "servicePointId";

  private final String loanId;
  private final ZonedDateTime declaredLostDateTime;
  private final String comment;
  private final String servicePointId;

  public static Result<DeclareItemLostRequest> from(JsonObject json, String loanId) {
    log.debug("from:: parameters json: {}, loanId: {}", () -> asJson(json), () -> loanId);

    final String comment = getProperty(json, "comment");

    String servicePointId = getProperty(json, "servicePointId");

    if (StringUtils.isBlank(servicePointId)) {
      log.info("from:: servicePointId is blank");
      return failedValidation("A service point is required for item to be declared lost", SERVICE_POINT_ID, servicePointId);
    }

    final ZonedDateTime dateTime;
    try {
      dateTime = getDateTimeProperty(json, DECLARED_LOST_DATETIME);
    } catch (Exception e) {
        return failedValidation(
          e.getMessage(), DECLARED_LOST_DATETIME,
          getProperty(json, DECLARED_LOST_DATETIME));
      }

    Result<DeclareItemLostRequest> result = succeeded(new DeclareItemLostRequest(loanId, dateTime,
      comment, getProperty(json, "servicePointId")));
    log.info("from:: result {}", () -> resultAsString(result));
    return result;
  }

  public String getServicePointId() {
    return this.servicePointId;
  }
}
