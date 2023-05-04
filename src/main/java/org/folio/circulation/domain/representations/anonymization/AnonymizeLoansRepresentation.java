
package org.folio.circulation.domain.representations.anonymization;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.results.Result;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private AnonymizeLoansRepresentation() { }

  public static Result<JsonObject> from(Result<LoanAnonymizationRecords> records) {
    log.debug("from:: parameters records: {}", () -> resultAsString(records));
    return records.map(AnonymizeLoansRepresentation::mapToJson)
      .orElse(failed(records.cause()));
  }

  private static Result<JsonObject> mapToJson(LoanAnonymizationRecords records) {
    log.debug("mapToJson:: parameters records: {}", records);
    LoanAnonymizationAPIResponse response = new LoanAnonymizationAPIResponse();
    response
        .withAnonymizedLoans(records.getAnonymizedLoanIds())
        .withErrors(mapToErrors(records.getNotAnonymizedLoans()));

    return of(() -> JsonObject.mapFrom(response));

  }

  private static List<Error> mapToErrors(Map<String, Collection<String>> multiMap) {
    log.debug("mapToErrors:: parameters multiMap: {}", () -> mapAsString(multiMap));
    return multiMap.keySet()
      .stream()
      .map(k -> new Error().withMessage(k)
        .withParameters(Collections.singletonList(new Parameter().withKey("loanIds")
          // errors.schema defines value as String so we need to serialize the JsonArray
          .withValue(new JsonArray(List.copyOf(multiMap.get(k))).toString()))))
      .collect(Collectors.toList());
  }
}
