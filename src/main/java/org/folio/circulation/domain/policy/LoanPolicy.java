package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.resources.RenewalValidator;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class LoanPolicy extends Policy {
  private static final String LOANS_POLICY_KEY = "loansPolicy";
  private static final String PERIOD_KEY = "period";

  private static final String REQUEST_MANAGEMENT_KEY = "requestManagement";
  private static final String HOLDS_KEY = "holds";
  private static final String ALTERNATE_RENEWAL_LOAN_PERIOD_KEY = "alternateRenewalLoanPeriod";

  private static final String INTERVAL_ID = "intervalId";
  private static final String DURATION = "duration";
  private static final String ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY = "alternateCheckoutLoanPeriod";
  private static final String GRACE_PERIOD_KEY = "gracePeriod";

  private static final String KEY_ERROR_TEXT = "the \"%s\" in the holds is not recognized";
  private static final String INTERVAL_ERROR_TEXT = "the interval \"%s\" in \"%s\" is not recognized";
  private static final String DURATION_ERROR_TEXT = "the duration \"%s\" in \"%s\" is invalid";

  private final JsonObject representation;
  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final FixedDueDateSchedules alternateRenewalFixedDueDateSchedules;
  private AppliedRuleConditions ruleConditionsEntity;

  private LoanPolicy(JsonObject representation) {
    this(representation,
      new NoFixedDueDateSchedules(),
      new NoFixedDueDateSchedules());
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules,
    FixedDueDateSchedules alternateRenewalFixedDueDateSchedules) {
    super(getProperty(representation, "id"), getProperty(representation, "name"));
    this.representation = representation;
    this.fixedDueDateSchedules = fixedDueDateSchedules;
    this.alternateRenewalFixedDueDateSchedules = alternateRenewalFixedDueDateSchedules;
  }

  LoanPolicy(
    JsonObject representation,
    FixedDueDateSchedules fixedDueDateSchedules,
    FixedDueDateSchedules alternateRenewalFixedDueDateSchedules,
    AppliedRuleConditions ruleConditionsEntity) {

    super(getProperty(representation, "id"), getProperty(representation, "name"));
    this.representation = representation;
    this.fixedDueDateSchedules = fixedDueDateSchedules;
    this.alternateRenewalFixedDueDateSchedules = alternateRenewalFixedDueDateSchedules;
    this.ruleConditionsEntity = ruleConditionsEntity;
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

  public boolean hasRenewalPeriod() {
    return useDifferentPeriod()
      && getRenewalsPolicy() != null
      && getRenewalsPolicy().containsKey(PERIOD_KEY);
  }

  public boolean isHoldRequestRenewable() {
    boolean renewItemsWithRequest = false;
    if (representation != null && representation.containsKey(REQUEST_MANAGEMENT_KEY)) {
      JsonObject requestManagement = representation.getJsonObject(REQUEST_MANAGEMENT_KEY);
      JsonObject holds = requestManagement.getJsonObject(HOLDS_KEY);
      renewItemsWithRequest = getBooleanProperty(holds, "renewItemsWithRequest");
    }
    return renewItemsWithRequest;
  }

  public boolean isNotRenewable() {
    return !getBooleanProperty(representation, "renewable");
  }

  public void errorWhenReachedRenewalLimit(Loan loan, List<ValidationError> errors) {
    if (hasReachedRenewalLimit(loan)) {
      errors.add(loanPolicyValidationError("loan at maximum renewal number"));
    }
  }

  public boolean hasReachedRenewalLimit(Loan loan) {
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

  public DueDateStrategy determineStrategy(
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
          systemDate, getRenewFrom(), getRenewalPeriod(loansPolicy, renewalsPolicy, isRenewalWithHoldRequest),
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
      if (isRenewal) {
        return new FixedScheduleRenewalDueDateStrategy(getId(), getName(),
          getRenewalFixedDueDateSchedules(), systemDate, this::loanPolicyValidationError);
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

  private ValidationError loanPolicyValidationError(String message) {
    return RenewalValidator.loanPolicyValidationError(this, message);
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
    if (isRenewalWithHoldRequest && hasAlternateRenewalLoanPeriodForHolds()) {
      result = getAlternateRenewalLoanPeriodForHolds();
    } else {
      result = useDifferentPeriod()
        ? getPeriod(renewalsPolicy)
        : getPeriod(loansPolicy);
    }
    return result;
  }

  public boolean hasAlternateRenewalLoanPeriodForHolds() {
    return getHolds().containsKey(ALTERNATE_RENEWAL_LOAN_PERIOD_KEY);
  }

  private Period getAlternateRenewalLoanPeriodForHolds() {
    return getPeriod(getHolds(), ALTERNATE_RENEWAL_LOAN_PERIOD_KEY);
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

  public boolean isFixed() {
    return isProfile(getLoansPolicy(), "Fixed");
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

  public DueDateManagement getDueDateManagement() {
    JsonObject loansPolicyObj = getLoansPolicy();
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

  public Period getGracePeriod() {
    return getPeriod(getLoansPolicy(), GRACE_PERIOD_KEY);
  }

  private LoanPolicyPeriod getPeriod(String val) {
    JsonObject loansPolicyObj = getLoansPolicy();
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

  public Integer getItemLimit() {
    final JsonObject loansPolicy = getLoansPolicy();
    if (loansPolicy == null) {
      return null;
    }
    return getLoansPolicy().getInteger("itemLimit");
  }

  public AppliedRuleConditions getRuleConditions() {
    return ruleConditionsEntity;
  }
}
