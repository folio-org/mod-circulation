package org.folio.circulation.domain.anonymization.config;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LoanAnonymizationConfiguration {
  private static final String FEEFINE = "feeFine";

  private final JsonObject representation;
  private final ClosingType loanClosingType;
  private final ClosingType feesAndFinesClosingType;
  private final boolean treatLoansWithFeesAndFinesDifferently;
  private final Period loanClosePeriod;
  private final Period feeFineClosePeriod;

  public static LoanAnonymizationConfiguration from(JsonObject jsonObject) {
    return new LoanAnonymizationConfiguration(jsonObject,
        ClosingType.from(getNestedStringProperty(jsonObject, "closingType", "loan")),
        ClosingType.from(getNestedStringProperty(jsonObject, "closingType", FEEFINE)),
        getBooleanProperty(jsonObject, "treatEnabled"),
        Period.from(getNestedIntegerProperty(jsonObject, "loan", "duration"),
            getNestedStringProperty(jsonObject, "loan", "intervalId")),
        Period.from(
            getNestedIntegerProperty(jsonObject, FEEFINE, "duration"),
            getNestedStringProperty(jsonObject, FEEFINE, "intervalId")));
  }

  public JsonObject getRepresentation() {
    return representation;
  }

  public ClosingType getLoanClosingType() {
    return loanClosingType;
  }

  public boolean treatLoansWithFeesAndFinesDifferently() {
    return treatLoansWithFeesAndFinesDifferently;
  }

  public Period getFeeFineClosePeriod() {
    return feeFineClosePeriod;
  }

  public Period getLoanClosePeriod() {
    return loanClosePeriod;
  }

  public ClosingType getFeesAndFinesClosingType() {
    return feesAndFinesClosingType;
  }
}
