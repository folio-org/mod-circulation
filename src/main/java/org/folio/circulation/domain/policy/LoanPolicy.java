package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.folio.circulation.domain.policy.LoansPolicyProfile.getProfileByName;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedResult;

public class LoanPolicy {

  private static final String LOANS_POLICY_KEY = "loansPolicy";
  private static final String PERIOD_KEY = "period";

  private final JsonObject representation;
  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final FixedDueDateSchedules alternateRenewalFixedDueDateSchedules;

  private LoanPolicy(JsonObject representation) {
    this(representation,
      new NoFixedDueDateSchedules(),
      new NoFixedDueDateSchedules());
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules,
    FixedDueDateSchedules alternateRenewalFixedDueDateSchedules) {

    this.representation = representation;
    this.fixedDueDateSchedules = fixedDueDateSchedules;
    this.alternateRenewalFixedDueDateSchedules = alternateRenewalFixedDueDateSchedules;
  }

  static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  //TODO: make this have similar signature to renew
  public HttpResult<DateTime> calculateInitialDueDate(Loan loan) {
    return determineStrategy(false, null).calculateDueDate(loan);
  }

  public HttpResult<Loan> renew(Loan loan, DateTime systemDate) {
    //TODO: Create HttpResult wrapper that traps exceptions
    try {
      if(isNotRenewable()) {
        return failedResult(errorForPolicy("loan is not renewable"));
      }

      final HttpResult<DateTime> proposedDueDateResult =
        determineStrategy(true, systemDate).calculateDueDate(loan);

      List<ValidationError> errors = new ArrayList<>();

      //TODO: Need a more elegent way of combining validation errors
      if(proposedDueDateResult.failed()) {
        if (proposedDueDateResult.cause() instanceof ValidationErrorFailure) {
          ValidationErrorFailure failureCause =
            (ValidationErrorFailure) proposedDueDateResult.cause();

          errors.addAll(failureCause.getErrors());
        }
      }
      else {
        errorWhenEarlierOrSameDueDate(loan, proposedDueDateResult.value(), errors);
      }

      errorWhenReachedRenewalLimit(loan, errors);

      if(errors.isEmpty()) {
        return proposedDueDateResult.map(dueDate -> loan.renew(dueDate, getId()));
      }
      else {
        return HttpResult.failed(new ValidationErrorFailure(errors));
      }
    }
    catch(Exception e) {
      return failed(new ServerErrorFailure(e));
    }
  }

  public HttpResult<Loan> overrideRenewal(Loan loan, DateTime systemDate,
                                          DateTime overrideDueDate, String comment) {
    try {
      if (isNotRenewable()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }
      final HttpResult<DateTime> proposedDueDateResult =
        determineStrategy(true, systemDate).calculateDueDate(loan);

      final JsonObject loansPolicy = getLoansPolicy();

      if (proposedDueDateResult.failed() && isFixed(loansPolicy)) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }
      
      if (proposedDueDateResult.failed() && isRolling(loansPolicy)) {
        DueDateStrategy dueDateStrategy = getRollingRenewalOverrideDueDateStrategy(systemDate);
        return dueDateStrategy.calculateDueDate(loan)
          .map(dueDate -> loan.overrideRenewal(dueDate, getId(), comment));
      }

      if (proposedDueDateResult.succeeded() &&
        reachedNumberOfRenewalsLimit(loan) && !unlimitedRenewals()) {
        return proposedDueDateResult.map(dueDate -> loan.overrideRenewal(dueDate, getId(), comment));
      }

      return HttpResult.failed(new ValidationErrorFailure(errorForNotMatchingOverrideCases()));

    } catch (Exception e) {
      return failed(new ServerErrorFailure(e));
    }
  }

  private HttpResult<Loan> overrideRenewalForDueDate(Loan loan, DateTime overrideDueDate, String comment) {
    if (overrideDueDate == null) {
      return HttpResult.failed(new ValidationErrorFailure(errorForDueDate()));
    }
    return HttpResult.succeeded(loan.overrideRenewal(overrideDueDate, getId(), comment));
  }

  private DueDateStrategy getRollingRenewalOverrideDueDateStrategy(DateTime systemDate) {
    final JsonObject loansPolicy = getLoansPolicy();
    final JsonObject renewalsPolicy = getRenewalsPolicy();
    return new RollingRenewalOverrideDueDateStrategy(getId(), getName(),
      systemDate, getRenewFrom(), getRenewalPeriod(loansPolicy, renewalsPolicy),
      getRenewalDueDateLimitSchedules(), this::errorForPolicy);
  }

  private ValidationError errorForDueDate() {
    HashMap<String, String> parameters = new HashMap<>();
    parameters.put("dueDate", null);

    String reason = "New due date must be specified when due date calculation fails";
    return new ValidationError(reason, parameters);
  }

  private ValidationError errorForNotMatchingOverrideCases() {
    String reason = "Override renewal does not match any of expected cases: " +
      "item is not renewable, " +
      "reached number of renewals limit or " +
      "renewal date falls outside of the date ranges in the loan policy";
    return new ValidationError(reason, new HashMap<>());
  }

  private ValidationError errorForPolicy(String reason) {
    HashMap<String, String> parameters = new HashMap<>();
    parameters.put("loanPolicyId", getId());
    parameters.put("loanPolicyName", getName());

    return new ValidationError(reason, parameters);
  }

  private boolean isNotRenewable() {
    return !getBooleanProperty(representation, "renewable");
  }

  private void errorWhenReachedRenewalLimit(Loan loan, List<ValidationError> errors) {
    if(!unlimitedRenewals() && reachedNumberOfRenewalsLimit(loan)) {
      errors.add(errorForPolicy("loan has reached its maximum number of renewals"));
    }
  }

  private void errorWhenEarlierOrSameDueDate(
    Loan loan,
    DateTime proposedDueDate,
    List<ValidationError> errors) {

    if(isSameOrBefore(loan, proposedDueDate)) {
      errors.add(errorForPolicy(
        "renewal would not change the due date"));
    }
  }

  private boolean isSameOrBefore(Loan loan, DateTime proposedDueDate) {
    return proposedDueDate.isEqual(loan.getDueDate())
      || proposedDueDate.isBefore(loan.getDueDate());
  }

  private boolean reachedNumberOfRenewalsLimit(Loan loan) {
    return loan.getRenewalCount() >= getRenewalLimit();
  }

  private boolean unlimitedRenewals() {
    return getBooleanProperty(getRenewalsPolicy(), "unlimited");
  }

  private Integer getRenewalLimit() {
    return getIntegerProperty(getRenewalsPolicy(), "numberAllowed", 0);
  }

  private DueDateStrategy determineStrategy(boolean isRenewal, DateTime systemDate) {
    final JsonObject loansPolicy = getLoansPolicy();
    final JsonObject renewalsPolicy = getRenewalsPolicy();

    //TODO: Temporary until have better logic for missing loans policy
    if(loansPolicy == null) {
      return new UnknownDueDateStrategy(getId(), getName(), "", isRenewal,
        this::errorForPolicy);
    }

    if(isRolling(loansPolicy)) {
      if(isRenewal) {
        return new RollingRenewalDueDateStrategy(getId(), getName(),
          systemDate, getRenewFrom(), getRenewalPeriod(loansPolicy, renewalsPolicy),
          getRenewalDueDateLimitSchedules(), this::errorForPolicy);
      }
      else {
        return new RollingCheckOutDueDateStrategy(getId(), getName(),
          getPeriod(loansPolicy), fixedDueDateSchedules, this::errorForPolicy);
      }
    }
    else if(isFixed(loansPolicy)) {
      if(isRenewal) {
        return new FixedScheduleRenewalDueDateStrategy(getId(), getName(),
          getRenewalFixedDueDateSchedules(), systemDate, this::errorForPolicy);
      }
      else {
        return new FixedScheduleCheckOutDueDateStrategy(getId(), getName(),
          fixedDueDateSchedules, this::errorForPolicy);
      }
    }
    else {
      return new UnknownDueDateStrategy(getId(), getName(),
        getProfileId(loansPolicy), isRenewal, this::errorForPolicy);
    }
  }

  private JsonObject getLoansPolicy() {
    return representation.getJsonObject(LOANS_POLICY_KEY);
  }

  private JsonObject getRenewalsPolicy() {
    return representation.getJsonObject("renewalsPolicy");
  }

  private FixedDueDateSchedules getRenewalDueDateLimitSchedules() {
    if(useDifferentPeriod()) {
      if(Objects.isNull(alternateRenewalFixedDueDateSchedules)
        || alternateRenewalFixedDueDateSchedules instanceof NoFixedDueDateSchedules)
        return fixedDueDateSchedules;
      else {
        return alternateRenewalFixedDueDateSchedules;
      }
    }
    else {
      return fixedDueDateSchedules;
    }
  }

  private Period getRenewalPeriod(
    JsonObject loansPolicy,
    JsonObject renewalsPolicy) {

    return useDifferentPeriod()
      ? getPeriod(renewalsPolicy)
      : getPeriod(loansPolicy);
  }

  private Period getPeriod(JsonObject policy) {
    String interval = getNestedStringProperty(policy, PERIOD_KEY, "intervalId");
    Integer duration = getNestedIntegerProperty(policy, PERIOD_KEY, "duration");
    return Period.from(duration, interval);
  }

  private Boolean useDifferentPeriod() {
    return getBooleanProperty(getRenewalsPolicy(), "differentPeriod");
  }

  private String getRenewFrom() {
    return getProperty(getRenewalsPolicy(), "renewFromId");
  }

  private FixedDueDateSchedules getRenewalFixedDueDateSchedules() {
    return useDifferentPeriod()
      ? alternateRenewalFixedDueDateSchedules
      : fixedDueDateSchedules;
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

  LoanPolicy withDueDateSchedules(FixedDueDateSchedules loanSchedules) {
    return new LoanPolicy(representation, loanSchedules,
      alternateRenewalFixedDueDateSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return withDueDateSchedules(FixedDueDateSchedules.from(fixedDueDateSchedules));
  }

  LoanPolicy withAlternateRenewalSchedules(FixedDueDateSchedules renewalSchedules) {
    return new LoanPolicy(representation, fixedDueDateSchedules, renewalSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  LoanPolicy withAlternateRenewalSchedules(JsonObject renewalSchedules) {
    return withAlternateRenewalSchedules(FixedDueDateSchedules.from(renewalSchedules));
  }

  public boolean isLoanable() {
    return representation.getBoolean("loanable", false);
  }

  public LoansPolicyProfile getLoansPolicyProfile() {
    JsonObject loansPolicyObj = representation.getJsonObject(LOANS_POLICY_KEY);
    if (Objects.isNull(loansPolicyObj)) {
      return LoansPolicyProfile.INCORRECT;
    }

    String profileId = loansPolicyObj.getString("profileId");
    return getProfileByName(profileId);
  }

  public List<DateTime> getFixedDueDates() {
    return fixedDueDateSchedules.getDueDates();
  }

  public DueDateManagement getDueDateManagement() {
    JsonObject loansPolicyObj = representation.getJsonObject(LOANS_POLICY_KEY);
    if (Objects.isNull(loansPolicyObj)) {
      return DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
    }

    String dateManagementId = loansPolicyObj.getString("closedLibraryDueDateManagementId");
    return DueDateManagement.getDueDateManagement(dateManagementId);
  }

  public LoanPolicyPeriod getPeriodInterval() {
    return getPeriod(PERIOD_KEY);
  }

  public int getPeriodDuration() {
    return getDuration(PERIOD_KEY);
  }

  public int getOffsetPeriodDuration() {
    return getDuration("openingTimeOffset");
  }

  public LoanPolicyPeriod getOffsetPeriodInterval() {
    return getPeriod("openingTimeOffset");
  }

  private LoanPolicyPeriod getPeriod(String val) {
    JsonObject loansPolicyObj = representation.getJsonObject(LOANS_POLICY_KEY);
    if (Objects.isNull(loansPolicyObj)) {
      return LoanPolicyPeriod.INCORRECT;
    }

    JsonObject period = loansPolicyObj.getJsonObject(val);
    if (Objects.isNull(period)) {
      return LoanPolicyPeriod.INCORRECT;
    }

    String intervalId = period.getString("intervalId");
    return LoanPolicyPeriod.getProfileByName(intervalId);
  }

  private int getDuration(String val) {
    JsonObject loansPolicyObj = representation.getJsonObject(LOANS_POLICY_KEY);
    JsonObject period = loansPolicyObj.getJsonObject(val);
    if (Objects.isNull(period)) {
      return 0;
    }
    return period.getInteger("duration");
  }

  public String getId() {
    return representation.getString("id");
  }

  String getLoansFixedDueDateScheduleId() {
    return getProperty(getLoansPolicy(), "fixedDueDateScheduleId");
  }

  String getAlternateRenewalsFixedDueDateScheduleId() {
    return getProperty(getRenewalsPolicy(), "alternateFixedDueDateScheduleId");
  }
}
