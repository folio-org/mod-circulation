package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

@AllArgsConstructor(access = PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Getter
public class LoanHistory {
  private final String id;
  @ToString.Include
  private final JsonObject representation;

  private final String operation;

  private final Loan loan;

  public static LoanHistory from(JsonObject representation) {
    return new LoanHistory(getProperty(representation, "id"),
      representation, getProperty(representation, "operation"),
      Loan.from(getObjectProperty(representation, "loan")));
  }
}
