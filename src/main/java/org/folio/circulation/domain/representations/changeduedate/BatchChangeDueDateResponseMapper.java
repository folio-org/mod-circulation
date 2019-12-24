
package org.folio.circulation.domain.representations.changeduedate;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

import io.vertx.core.json.JsonArray;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public class BatchChangeDueDateResponseMapper {

  private static final String BATCH_DUE_DATE_FAILED_MESSAGE = "Batch due date failed";

  public static ResponseWritableResult from(
    List<String> failedIds) {

    return CollectionUtils.isEmpty(failedIds) ?
      NoContentResult.from(Result.succeeded("")) :
      failedValidation(
        new ValidationError(BATCH_DUE_DATE_FAILED_MESSAGE, "loanIds",
          new JsonArray(failedIds).toString())
      );
  }
}
