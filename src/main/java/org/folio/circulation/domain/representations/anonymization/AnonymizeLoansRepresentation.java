
package org.folio.circulation.domain.representations.anonymization;

import static org.folio.circulation.support.Result.failed;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.json.JSONArray;
import org.folio.circulation.domain.representations.Error;

import io.vertx.core.json.JsonObject;

public class AnonymizeLoansRepresentation {

  private AnonymizeLoansRepresentation() { }

  public static ResponseWritableResult<JsonObject> from(Result<LoanAnonymizationRecords> records) {
    return records.map(AnonymizeLoansRepresentation::mapToJson)
      .orElse(failed(records.cause()));
  }

  private static ResponseWritableResult<JsonObject> mapToJson(LoanAnonymizationRecords records) {
    LoanAnonymizationAPIResponse response = new LoanAnonymizationAPIResponse();
    response
        .withAnonymizedLoans(records.getAnonymizedLoans())
        .withErrors(mapToErrors(records.getNotAnonymizedLoans()));
    return new OkJsonResponseResult(JsonObject.mapFrom(response));

  }

  private static List<Error> mapToErrors(
      Map<String, Collection<String>> multiMap) {

    return multiMap.keySet()
      .stream()
      .map(k -> new Error().withMessage(k)
        .withParameters(Collections.singletonList(new Parameter().withKey("loanIds")
          .withValue(new JSONArray(multiMap.get(k)).toString()))))
      .collect(Collectors.toList());

  }
}
