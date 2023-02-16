package org.folio.circulation.domain.representations.anonymization;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;

import io.vertx.core.json.JsonArray;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class AnonymizeLoansRepresentationTest {

  @Test
  void errors() {
    Map<String, Set<String>> errorMap = Map.of(
        "foo", Set.of("foo1", "foo2"),
        "bar", Set.of("bar1"));
    Result<LoanAnonymizationRecords> result = Result.succeeded(
        new LoanAnonymizationRecords().withNotAnonymizedLoans(errorMap));

    JsonArray errors = AnonymizeLoansRepresentation.from(result).value().getJsonArray("errors");

    assertThat(errors.size(), is(2));
    // accept in any order
    if ("bar".equals(errors.getJsonObject(0).getString("message"))) {
      Collections.swap(errors.getList(), 0, 1);
    }

    assertThat(errors.getJsonObject(0).getString("message"), is("foo"));
    var parameters = errors.getJsonObject(0).getJsonArray("parameters");
    assertThat(parameters.getJsonObject(0).getString("key"), is("loanIds"));
    assertThat(parameters.getJsonObject(0).getString("value"),
        is(oneOf("[\"foo1\",\"foo2\"]", "[\"foo2\",\"foo1\"]")));

    assertThat(errors.getJsonObject(1).getString("message"), is("bar"));
    parameters = errors.getJsonObject(1).getJsonArray("parameters");
    assertThat(parameters.getJsonObject(0).getString("key"), is("loanIds"));
    assertThat(parameters.getJsonObject(0).getString("value"), is("[\"bar1\"]"));
  }

}
