package org.folio.circulation.domain.representations.changeduedate;

import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchChangeDueDateRequest {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String LOAN_IDS = "loanIds";
  private static final String DUE_DATE = "dueDate";

  private final List<String> loanIds;
  private final DateTime dueDate;

  private BatchChangeDueDateRequest(List<String> loanIds,
    DateTime dueDate) {

    this.loanIds = loanIds;
    this.dueDate = dueDate;
  }

  public static Result<BatchChangeDueDateRequest> from(RoutingContext routingContext) {

    List<String> loanIds;
    DateTime dueDate;
    try {
      JsonObject payload = routingContext.getBodyAsJson();
      if (payload == null) {
        return failedValidation("No payload provided",
          "", null);
      }

      JsonArray jsonArray = payload.getJsonArray(LOAN_IDS);
      if (jsonArray == null || jsonArray.isEmpty()) {
        return failedValidation("No loan ids provided",
          LOAN_IDS, null);
      }

      loanIds = jsonArray.getList();
      dueDate = getDateTimeProperty(payload, DUE_DATE);

      if (dueDate == null) {
        return failedValidation("No due date provided",
          DUE_DATE, null);
      }

    } catch (Exception e) {
      String msg = "Exception during changing due date";
      log.error(msg, e);
      return failedValidation("Exception during changing due date",
        "message", e.getMessage());
    }

    return succeeded(new BatchChangeDueDateRequest(loanIds, dueDate));
  }

  public List<String> getLoanIds() {
    return loanIds;
  }

  public DateTime getDueDate() {
    return dueDate;
  }
}
