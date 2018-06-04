package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonPropertyFetcher;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

public class LoanPolicy {
  private final JsonObject representation;
  private final FixedDueDateSchedules fixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation, null);
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules) {

    this.representation = representation;
    this.fixedDueDateSchedules = fixedDueDateSchedules;
  }

  static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  //TODO: make this have similar signature to renew
  public HttpResult<DateTime> calculateInitialDueDate(Loan loan) {
    return determineStrategy(false, null).calculateDueDate(loan);
  }

  public HttpResult<Loan> renew(Loan loan, DateTime systemDate) {
    return determineStrategy(true, systemDate).calculateDueDate(loan)
      .map(dueDate -> loan.renew(dueDate, getId()));
  }

  private DueDateStrategy determineStrategy(boolean isRenewal, DateTime systemDate) {
    final JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    //TODO: Temporary until have better logic for missing loans policy
    if(loansPolicy == null) {
      return new UnknownDueDateStrategy(getId(), getName(), "", isRenewal);
    }

    if(isRolling(loansPolicy)) {
      final String interval = getNestedStringProperty(loansPolicy, "period", "intervalId");
      final Integer duration = getNestedIntegerProperty(loansPolicy, "period", "duration");

      if(isRenewal) {
        return new RollingRenewalDueDateStrategy(getId(), getName(),
          interval, duration, systemDate, getRenewFrom());
      }
      else {
        return new RollingCheckOutDueDateStrategy(getId(), getName(),
          interval, duration, fixedDueDateSchedules);
      }
    }
    else if(isFixed(loansPolicy)) {
      return new FixedScheduleDueDateStrategy(getId(), getName(),
        fixedDueDateSchedules);
    }
    else {
      return new UnknownDueDateStrategy(getId(), getName(),
        getProfileId(loansPolicy), isRenewal);
    }
  }

  private String getRenewFrom() {
    return JsonPropertyFetcher.getNestedStringProperty(representation, "renewalsPolicy", "renewFromId");
  }

  private String getProfileId(JsonObject loansPolicy) {
    return loansPolicy.getString("profileId");
  }

  private String getName() {
    return representation.getString("name");
  }

  private boolean isFixed(JsonObject loansPolicy) {
    return isProfile(loansPolicy, "Fixed");
  }

  private boolean isRolling(JsonObject loansPolicy) {
    return isProfile(loansPolicy, "Rolling");
  }

  private boolean isProfile(JsonObject loansPolicy, String profileId) {
    return StringUtils.equalsIgnoreCase(getProfileId(loansPolicy), profileId);
  }

  LoanPolicy withDueDateSchedules(FixedDueDateSchedules fixedDueDateSchedules) {
    return new LoanPolicy(representation, fixedDueDateSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return withDueDateSchedules(new FixedDueDateSchedules(fixedDueDateSchedules));
  }

  public String getId() {
    return representation.getString("id");
  }

  String getLoansFixedDueDateScheduleId() {
    return getNestedStringProperty(representation, "loansPolicy",
      "fixedDueDateScheduleId");
  }
}
