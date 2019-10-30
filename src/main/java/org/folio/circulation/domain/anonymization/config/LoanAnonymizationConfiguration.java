package org.folio.circulation.domain.anonymization.config;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;

public class LoanAnonymizationConfiguration {

  private static final String FEEFINE = "feeFine";
  private JsonObject representation;
  private ClosingType loanClosingType;
  private ClosingType feesAndFinesClosingType;
  private boolean treatLoansWithFeesAndFinesDifferently;
  private Period loanClosePeriod;
  private Period feeFineClosePeriod;

  private LoanAnonymizationConfiguration(JsonObject representation) {
    this.representation = representation;
    this.feesAndFinesClosingType = ClosingType.from(
        getNestedStringProperty(representation, "closingType", FEEFINE));
    this.loanClosingType = ClosingType.from(
        getNestedStringProperty(representation, "closingType", "loan"));
    this.treatLoansWithFeesAndFinesDifferently = getBooleanProperty(representation, "treatEnabled");
    this.loanClosePeriod = Period.from(
      getNestedIntegerProperty(representation, "loan", "duration"),
      getNestedStringProperty(representation, "loan", "intervalId"));
    this.feeFineClosePeriod = Period.from(
      getNestedIntegerProperty(representation, FEEFINE, "duration"),
      getNestedStringProperty(representation, FEEFINE, "intervalId"));
  }

  public static LoanAnonymizationConfiguration from(JsonObject jsonObject) {
    return new LoanAnonymizationConfiguration(jsonObject);
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
