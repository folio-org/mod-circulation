package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class LoanPolicy {
  private static final String LOANS_POLICY_KEY = "loansPolicy";
  private static final String PERIOD_KEY = "period";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE = "renewal would not change the due date";

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

  public static LoanPolicy from(JsonObject representation) {
    return new LoanPolicy(representation);
  }

  public static LoanPolicy unknown(String id) {
    return new UnknownLoanPolicy(id);
  }

  //TODO: make this have similar signature to renew
  public Result<DateTime> calculateInitialDueDate(Loan loan) {
    return determineStrategy(false, null).calculateDueDate(loan);
  }

  public Result<Loan> renew(Loan loan, DateTime systemDate, RequestQueue requestQueue) {
    //TODO: Create HttpResult wrapper that traps exceptions
    try {
      List<ValidationError> errors = new ArrayList<>();

      Request firstRequest = requestQueue.getRequests().stream()
        .findFirst().orElse(null);

      if (hasRecallRequest(firstRequest)) {
        String reason = "items cannot be renewed when there is an active recall request";
        errors.add(errorForRecallRequest(reason, firstRequest.getId()));
      }

      if (isNotLoanable()) {
        errors.add(errorForPolicy("item is not loanable"));
        return failedValidation(errors);
      }
      if (isNotRenewable()) {
        errors.add(errorForPolicy("loan is not renewable"));
        return failedValidation(errors);
      }

      final Result<DateTime> proposedDueDateResult =
        determineStrategy(true, systemDate).calculateDueDate(loan);

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
        return failedValidation(errors);
      }
    }
    catch(Exception e) {
      return failedDueToServerError(e);
    }
  }

  public Result<Loan> overrideRenewal(Loan loan, DateTime systemDate,
                                      DateTime overrideDueDate, String comment,
                                      boolean hasRecallRequest) {
    try {
      if (isNotLoanable() || isNotRenewable()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }
      final Result<DateTime> proposedDueDateResult =
        determineStrategy(true, systemDate).calculateDueDate(loan);

      if (proposedDueDateResult.failed()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }

      if (hasReachedRenewalLimit(loan)) {
        return processRenewal(proposedDueDateResult, loan, comment);
      }

      if (hasRecallRequest) {
        return processRenewal(proposedDueDateResult, loan, comment);
      }

      return failedValidation(errorForNotMatchingOverrideCases());

    } catch (Exception e) {
      return failedDueToServerError(e);
    }
  }

  private Result<Loan> processRenewal(Result<DateTime> calculatedDueDate, Loan loan, String comment) {
    return calculatedDueDate
      .next(dueDate -> errorWhenEarlierOrSameDueDate(loan, dueDate))
      .map(dueDate -> loan.overrideRenewal(dueDate, getId(), comment));
  }

  private Result<DateTime> errorWhenEarlierOrSameDueDate(Loan loan, DateTime proposedDueDate) {
    if (isSameOrBefore(loan, proposedDueDate)) {
      return failedValidation(errorForPolicy(
        RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }
    return Result.succeeded(proposedDueDate);
  }

  private Result<Loan> overrideRenewalForDueDate(Loan loan, DateTime overrideDueDate, String comment) {
    if (overrideDueDate == null) {
      return failedValidation(errorForDueDate());
    }
    return succeeded(loan.overrideRenewal(overrideDueDate, getId(), comment));
  }

  private ValidationError errorForDueDate() {
    return new ValidationError(
      "New due date must be specified when due date calculation fails",
      "dueDate", "null");
  }

  private ValidationError errorForNotMatchingOverrideCases() {
    String reason = "Override renewal does not match any of expected cases: " +
      "item is not loanable, " +
      "item is not renewable, " +
      "reached number of renewals limit," +
      "renewal date falls outside of the date ranges in the loan policy, " +
      "items cannot be renewed when there is an active recall request";

    return errorForPolicy(reason);
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
    if (hasReachedRenewalLimit(loan)) {
      errors.add(errorForPolicy("loan at maximum renewal number"));
    }
  }

  private void errorWhenEarlierOrSameDueDate(
    Loan loan,
    DateTime proposedDueDate,
    List<ValidationError> errors) {

    if(isSameOrBefore(loan, proposedDueDate)) {
      errors.add(errorForPolicy(RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    }
  }

  private boolean isSameOrBefore(Loan loan, DateTime proposedDueDate) {
    return proposedDueDate.isEqual(loan.getDueDate())
      || proposedDueDate.isBefore(loan.getDueDate());
  }

  private boolean hasReachedRenewalLimit(Loan loan) {
    return reachedNumberOfRenewalsLimit(loan) && !unlimitedRenewals();
  }

  private boolean reachedNumberOfRenewalsLimit(Loan loan) {
    return loan.getRenewalCount() >= getRenewalLimit();
  }

  public boolean unlimitedRenewals() {
    return getBooleanProperty(getRenewalsPolicy(), "unlimited");
  }

  public Integer getRenewalLimit() {
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
    return getPeriod(policy, PERIOD_KEY);
  }

  private Period getPeriod(JsonObject policy, String periodKey) {
    String interval = getNestedStringProperty(policy, periodKey, "intervalId");
    Integer duration = getNestedIntegerProperty(policy, periodKey, "duration");
    return Period.from(duration, interval);
  }

  private boolean useDifferentPeriod() {
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

  public String getName() {
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
    return getBooleanProperty(representation, "loanable");
  }

  public boolean isNotLoanable() {
    return !isLoanable();
  }

  private boolean hasRecallRequest(Request firstRequest) {
    return firstRequest != null && firstRequest.getRequestType() == RECALL;
  }

  private ValidationError errorForRecallRequest(String reason, String requestId) {
    return new ValidationError(reason, "request id", requestId);
  }

  public DueDateManagement getDueDateManagement() {
    JsonObject loansPolicyObj = representation.getJsonObject(LOANS_POLICY_KEY);
    if (Objects.isNull(loansPolicyObj)) {
      return DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME;
    }

    String dateManagementId = loansPolicyObj.getString("closedLibraryDueDateManagementId");
    return DueDateManagement.getDueDateManagement(dateManagementId);
  }

  public LoanPolicyPeriod getPeriodInterval() {
    return getPeriod(PERIOD_KEY);
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

  public Optional<DateTime> getScheduleLimit(DateTime loanDate, boolean isRenewal, DateTime systemDate) {
    final JsonObject loansPolicy = getLoansPolicy();

    if(loansPolicy == null) {
      return Optional.empty();
    }

    if(isRolling(loansPolicy)) {
      if(isRenewal) {
        return getRenewalDueDateLimitSchedules().findDueDateFor(loanDate);
      }
      else {
        return fixedDueDateSchedules.findDueDateFor(loanDate);
      }
    }
    else if(isFixed(loansPolicy)) {
      if(isRenewal) {
        return getRenewalFixedDueDateSchedules().findDueDateFor(systemDate);
      }
      else {
        return fixedDueDateSchedules.findDueDateFor(loanDate);
      }
    }
    else {
      return Optional.empty();
    }
  }

  public Result<Loan> recall(Loan loan) {
    final JsonObject recalls = representation
        .getJsonObject("requestManagement", new JsonObject())
        .getJsonObject("recalls", new JsonObject());

    final Result<DateTime> minimumDueDateResult =
        getDueDate("minimumGuaranteedLoanPeriod", recalls,
            loan.getLoanDate(), null);

    final DateTime systemDate = ClockManager.getClockManager().getDateTime();

    final Result<DateTime> recallDueDateResult =
        getDueDate("recallReturnInterval", recalls, systemDate, systemDate);

    final List<ValidationError> errors = new ArrayList<>();

    errors.addAll(combineValidationErrors(recallDueDateResult));
    errors.addAll(combineValidationErrors(minimumDueDateResult));

    if (errors.isEmpty()) {
      return minimumDueDateResult
          .combine(recallDueDateResult, this::determineDueDate)
          .map(dueDate -> changeDueDate(dueDate, loan));
    } else {
      return failedValidation(errors);
    }
  }

  private DateTime determineDueDate(DateTime minimumGuaranteedDueDate,
      DateTime recallDueDate) {
    if (minimumGuaranteedDueDate == null ||
        recallDueDate.isAfter(minimumGuaranteedDueDate)) {
      return recallDueDate;
    } else {
      return minimumGuaranteedDueDate;
    }
  }

  private Loan changeDueDate(DateTime dueDate, Loan loan) {
    loan.changeDueDate(dueDate);
    return loan;
  }

  private Result<DateTime> getDueDate(
    String key,
    JsonObject representation,
    DateTime initialDateTime,
    DateTime defaultDateTime) {

    final Result<DateTime> result;

    if (representation.containsKey(key)) {
      result = getPeriod(representation, key).addTo(initialDateTime,
          () -> errorForPolicy(format("the \"%s\" in the loan policy is not recognized", key)),
          interval -> errorForPolicy(format("the interval \"%s\" in \"%s\" is not recognized", interval, key)),
          duration -> errorForPolicy(format("the duration \"%s\" in \"%s\" is invalid", duration, key)));
    } else {
      result = succeeded(defaultDateTime);
    }

    return result;
  }

  private List<ValidationError> combineValidationErrors(Result<?> result) {
    if(result.failed() && result.cause() instanceof ValidationErrorFailure) {
      final ValidationErrorFailure failureCause =
          (ValidationErrorFailure) result.cause();

      return new ArrayList<>(failureCause.getErrors());
    }

    return Collections.emptyList();
  }

  //TODO: Improve this to be a proper null object
  // requires significant rework of the loan policy interface
  private static class UnknownLoanPolicy extends LoanPolicy {
    UnknownLoanPolicy(String id) {
      super(new JsonObject().put("id", id));
    }
  }
}
