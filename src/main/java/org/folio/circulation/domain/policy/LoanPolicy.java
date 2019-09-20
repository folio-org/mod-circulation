package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
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

  private static final String REQUEST_MANAGEMENT_KEY = "requestManagement";
  private static final String HOLDS_KEY = "holds";
  private static final String ALTERNATE_RENEWAL_LOAN_PERIOD_KEY = "alternateRenewalLoanPeriod";
  public static final String CAN_NOT_RENEW_ITEM_ERROR =
    "Items with this loan policy cannot be renewed when there is an active, pending hold request";

  private static final String INTERVAL_ID = "intervalId";
  private static final String DURATION = "duration";
  private static final String ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY = "alternateCheckoutLoanPeriod";

  private static final String KEY_ERROR_TEXT = "the \"%s\" in the holds is not recognized";
  private static final String INTERVAL_ERROR_TEXT = "the interval \"%s\" in \"%s\" is not recognized";
  private static final String DURATION_ERROR_TEXT = "the duration \"%s\" in \"%s\" is invalid";

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
  public Result<DateTime> calculateInitialDueDate(Loan loan, RequestQueue requestQueue) {
    final DateTime systemTime = ClockManager.getClockManager().getDateTime();
    return determineStrategy(requestQueue, false, false, systemTime).calculateDueDate(loan);
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
        errors.add(loanPolicyValidationError("item is not loanable"));
        return failedValidation(errors);
      }
      if (isNotRenewable()) {
        errors.add(loanPolicyValidationError("loan is not renewable"));
        return failedValidation(errors);
      }
      boolean isRenewalWithHoldRequest = false;
      //Here can be either Hold request or null only
      if (isHold(firstRequest)) {
        if (!isHoldRequestRenewable()) {
          errors.add(loanPolicyValidationError(CAN_NOT_RENEW_ITEM_ERROR));
          return failedValidation(errors);
        }
        isRenewalWithHoldRequest = true;
      }

      final Result<DateTime> proposedDueDateResult =
        determineStrategy(null, true, isRenewalWithHoldRequest, systemDate)
          .calculateDueDate(loan);

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

  private boolean isHoldRequestRenewable() {
    boolean renewItemsWithRequest = false;
    if (representation != null && representation.containsKey(REQUEST_MANAGEMENT_KEY)) {
      JsonObject requestManagement = representation.getJsonObject(REQUEST_MANAGEMENT_KEY);
      JsonObject holds = requestManagement.getJsonObject(HOLDS_KEY);
      renewItemsWithRequest = getBooleanProperty(holds, "renewItemsWithRequest");
    }
    return renewItemsWithRequest;
  }

  private boolean isHold(Request request) {
    return request != null && request.getRequestType() == HOLD;
  }

  public Result<Loan> overrideRenewal(Loan loan, DateTime systemDate,
                                      DateTime overrideDueDate, String comment,
                                      boolean hasRecallRequest) {
    try {
      if (isNotLoanable() || isNotRenewable()) {
        return overrideRenewalForDueDate(loan, overrideDueDate, comment);
      }
      final Result<DateTime> proposedDueDateResult =
        determineStrategy(null, true, false, systemDate).calculateDueDate(loan);

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
      return failedValidation(loanPolicyValidationError(
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

    return loanPolicyValidationError(reason);
  }

  private ValidationError loanPolicyValidationError(String message) {
    return loanPolicyValidationError(message, Collections.emptyMap());
  }

  public ValidationError loanPolicyValidationError(
    String message, Map<String, String> additionalParameters) {

    Map<String, String> parameters = new HashMap<>(additionalParameters);
    parameters.put("loanPolicyId", getId());
    parameters.put("loanPolicyName", getName());
    return new ValidationError(message, parameters);
  }

  private boolean isNotRenewable() {
    return !getBooleanProperty(representation, "renewable");
  }

  private void errorWhenReachedRenewalLimit(Loan loan, List<ValidationError> errors) {
    if (hasReachedRenewalLimit(loan)) {
      errors.add(loanPolicyValidationError("loan at maximum renewal number"));
    }
  }

  private void errorWhenEarlierOrSameDueDate(
    Loan loan,
    DateTime proposedDueDate,
    List<ValidationError> errors) {

    if(isSameOrBefore(loan, proposedDueDate)) {
      errors.add(loanPolicyValidationError(RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
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

  private DueDateStrategy determineStrategy(
    RequestQueue requestQueue,
    boolean isRenewal,
    boolean isRenewalWithHoldRequest,
    DateTime systemDate) {

    final JsonObject loansPolicy = getLoansPolicy();
    final JsonObject renewalsPolicy = getRenewalsPolicy();
    final JsonObject holds = getHolds();

    //TODO: Temporary until have better logic for missing loans policy
    if(loansPolicy == null) {
      return new UnknownDueDateStrategy(getId(), getName(), "", isRenewal,
        this::loanPolicyValidationError);
    }

    if(isRolling(loansPolicy)) {
      if(isRenewal) {
        return new RollingRenewalDueDateStrategy(getId(), getName(),
          systemDate, getRenewFrom(), getRenewalPeriod(loansPolicy, renewalsPolicy,isRenewalWithHoldRequest),
          getRenewalDueDateLimitSchedules(), this::loanPolicyValidationError);
      }
      else {
        Period rollingPeriod = getPeriod(loansPolicy);
        if(isAlternatePeriod(requestQueue)) {
          rollingPeriod = getPeriod(holds, ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY);
        }

        return new RollingCheckOutDueDateStrategy(getId(), getName(),
          rollingPeriod, fixedDueDateSchedules, this::loanPolicyValidationError);
      }
    }
    else if(isFixed(loansPolicy)) {
      if(isRenewal) {
        if (isRenewalWithHoldRequest) {
          return new RollingRenewalDueDateStrategy(getId(), getName(), systemDate,
            "SYSTEM_DATE", getAlternateRenewalLoanPeriodForHolds(),
            new NoFixedDueDateSchedules(), this::loanPolicyValidationError);
        } else {
          return new FixedScheduleRenewalDueDateStrategy(getId(), getName(),
            getRenewalFixedDueDateSchedules(), systemDate, this::loanPolicyValidationError);
        }
      }
      else {
        if(isAlternatePeriod(requestQueue)) {
          return new RollingCheckOutDueDateStrategy(getId(), getName(),
            getPeriod(holds, ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY),
              fixedDueDateSchedules, this::loanPolicyValidationError);
        }
        else {
          return new FixedScheduleCheckOutDueDateStrategy(getId(), getName(),
            fixedDueDateSchedules, this::loanPolicyValidationError);
        }
      }
    }
    else {
      return new UnknownDueDateStrategy(getId(), getName(),
        getProfileId(loansPolicy), isRenewal, this::loanPolicyValidationError);
    }
  }

  private FixedDueDateSchedules buildAlternateDueDateSchedules(DateTime systemDate, JsonObject holds) {
    List<JsonObject> schedules =
      new ArrayList<>(fixedDueDateSchedules.getSchedules());
    schedules.add(0, buildSchedule(systemDate, holds));
    return new FixedDueDateSchedules("alternateDueDateSchedule", schedules);
  }

  private boolean isAlternatePeriod(RequestQueue requestQueue) {
    final JsonObject holds = getHolds();
    if(Objects.isNull(requestQueue)
      || !holds.containsKey(ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY)) {
      return false;
    }
    Optional<Request> potentialRequest = requestQueue.getRequests().stream().skip(1).findFirst();
    boolean isAlternateDueDateSchedule = false;
    if(potentialRequest.isPresent()) {
      Request request = potentialRequest.get();
      boolean isHold = request.getRequestType() == RequestType.HOLD;
      boolean isOpenNotYetFilled = request.getStatus() == RequestStatus.OPEN_NOT_YET_FILLED;
      if(isHold && isOpenNotYetFilled) {
        isAlternateDueDateSchedule = true;
      }
    }
    return isAlternateDueDateSchedule;
  }

  private JsonObject buildSchedule(DateTime systemDate, JsonObject request) {
    String key = ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY;
    Period duration = getPeriod(request, key);
    DateTime dueDate = duration.addTo(
        systemDate,
        () -> loanPolicyValidationError(format(KEY_ERROR_TEXT, key)),
        interval -> loanPolicyValidationError(format(INTERVAL_ERROR_TEXT, interval, key)),
        dur -> loanPolicyValidationError(format(DURATION_ERROR_TEXT, dur, key)))
          .value();

    Map<String, Object> scheduleProperties = new HashMap<>();
    // Ensure the schedule contains the system date
    scheduleProperties.put("from", systemDate.minusDays(1).toString());
    scheduleProperties.put("due", dueDate.toString());
    scheduleProperties.put("to", duration.addTo(
        dueDate,
        () -> loanPolicyValidationError(format(KEY_ERROR_TEXT, key)),
        interval -> loanPolicyValidationError(format(INTERVAL_ERROR_TEXT, interval, key)),
        dur -> loanPolicyValidationError(format(DURATION_ERROR_TEXT, dur, key)))
        .value().toString());
    return new JsonObject(scheduleProperties);

  }

  private JsonObject getLoansPolicy() {
    return representation.getJsonObject(LOANS_POLICY_KEY);
  }

  private JsonObject getRenewalsPolicy() {
    return representation.getJsonObject("renewalsPolicy");
  }

  private JsonObject getHolds() {
    return representation
      .getJsonObject(REQUEST_MANAGEMENT_KEY, new JsonObject())
      .getJsonObject(HOLDS_KEY, new JsonObject());
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
    JsonObject renewalsPolicy,
    boolean isRenewalWithHoldRequest) {

    Period result;
    if (isRenewalWithHoldRequest) {
      result = getAlternateRenewalLoanPeriodForHolds();
    } else {
      result = useDifferentPeriod() ? getPeriod(renewalsPolicy) : getPeriod(loansPolicy);
    }
    return result;
  }

  private Period getAlternateRenewalLoanPeriodForHolds() {
    JsonObject holds = representation
      .getJsonObject(REQUEST_MANAGEMENT_KEY)
      .getJsonObject(HOLDS_KEY);

    return getPeriod(holds, ALTERNATE_RENEWAL_LOAN_PERIOD_KEY);
  }

  private Period getPeriod(JsonObject policy) {
    return getPeriod(policy, PERIOD_KEY);
  }

  private Period getPeriod(JsonObject policy, String periodKey) {
    String interval = getNestedStringProperty(policy, periodKey, INTERVAL_ID);
    Integer duration = getNestedIntegerProperty(policy, periodKey, DURATION);
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
  public LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return withDueDateSchedules(FixedDueDateSchedules.from(fixedDueDateSchedules));
  }

  LoanPolicy withAlternateRenewalSchedules(FixedDueDateSchedules renewalSchedules) {
    return new LoanPolicy(representation, fixedDueDateSchedules, renewalSchedules);
  }

  //TODO: potentially remove this, when builder can create class or JSON representation
  public LoanPolicy withAlternateRenewalSchedules(JsonObject renewalSchedules) {
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

    String intervalId = period.getString(INTERVAL_ID);
    return LoanPolicyPeriod.getProfileByName(intervalId);
  }

  private int getDuration(String propertyName) {
    JsonObject period = getNestedObjectProperty(representation, LOANS_POLICY_KEY, propertyName);

    if (Objects.isNull(period)) {
      return 0;
    }
    return period.getInteger(DURATION);
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
    if (!loan.wasDueDateChangedByRecall()) {
      loan.changeDueDate(dueDate);
      loan.changeDueDateChangedByRecall();
    }

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
          () -> loanPolicyValidationError(format("the \"%s\" in the loan policy is not recognized", key)),
          interval -> loanPolicyValidationError(format(INTERVAL_ERROR_TEXT, interval, key)),
          duration -> loanPolicyValidationError(format(DURATION_ERROR_TEXT, duration, key)));
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
