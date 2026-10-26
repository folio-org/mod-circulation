package org.folio.circulation.domain.anonymization.config;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LoanAnonymizationConfiguration {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String FEEFINE = "feeFine";
  private static final String USER_SPECIFIC = "userSpecific";

  private final ClosingType loanClosingType;
  private final ClosingType feesAndFinesClosingType;
  private final boolean treatLoansWithFeesAndFinesDifferently;
  private final Period loanClosePeriod;
  private final Period feeFineClosePeriod;
  private final UserSpecificClosingType userSpecificClosingType;
  private final Period userSpecificClosePeriod;

  public static LoanAnonymizationConfiguration from(JsonObject jsonObject) {
    log.debug("from:: parsing loan anonymization configuration");

    return new LoanAnonymizationConfiguration(
      ClosingType.from(getNestedStringProperty(jsonObject, "closingType", "loan")),
      ClosingType.from(getNestedStringProperty(jsonObject, "closingType", FEEFINE)),
      getBooleanProperty(jsonObject, "treatEnabled"),
      closePeriod(jsonObject, "loan"),
      closePeriod(jsonObject, FEEFINE),
      UserSpecificClosingType.from(
        getNestedStringProperty(jsonObject, "closingType", USER_SPECIFIC)),
      closePeriod(jsonObject, USER_SPECIFIC));
  }

  private static Period closePeriod(JsonObject jsonObject, String key) {
    return Period.from(getNestedIntegerProperty(jsonObject, key, "duration"),
      getNestedStringProperty(jsonObject, key, "intervalId"));
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

  public UserSpecificClosingType getUserSpecificClosingType() {
    return userSpecificClosingType;
  }

  public Period getUserSpecificClosePeriod() {
    return userSpecificClosePeriod;
  }

  public boolean isUserSpecificAnonymizationEnabled() {
    return userSpecificClosingType != UserSpecificClosingType.DISABLED;
  }

  /**
   * The policy for users who chose "as early as possible": the user-specific rule replaces the
   * loan rule, the fee/fine rule is shared. An unknown user-specific rule anonymizes none of
   * these loans.
   */
  public LoanAnonymizationConfiguration forAsEarlyAsPossible() {
    ClosingType closingType = switch (userSpecificClosingType) {
      case IMMEDIATELY -> ClosingType.IMMEDIATELY;
      case INTERVAL -> ClosingType.INTERVAL;
      default -> ClosingType.UNKNOWN;
    };

    return new LoanAnonymizationConfiguration(closingType, feesAndFinesClosingType,
      treatLoansWithFeesAndFinesDifferently, userSpecificClosePeriod, feeFineClosePeriod,
      userSpecificClosingType, userSpecificClosePeriod);
  }
}
