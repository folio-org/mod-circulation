package org.folio.circulation.domain.policy;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.resources.RenewalValidator;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.storage.mappers.TimePeriodMapper;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class LoanPolicy extends Policy {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String LOANS_POLICY_KEY = "loansPolicy";
  private static final String PERIOD_KEY = "period";

  private static final String REQUEST_MANAGEMENT_KEY = "requestManagement";
  private static final String HOLDS_KEY = "holds";
  private static final String ALTERNATE_RENEWAL_LOAN_PERIOD_KEY = "alternateRenewalLoanPeriod";
  private static final String ALLOW_RECALLS_TO_EXTEND_OVERDUE_LOANS = "allowRecallsToExtendOverdueLoans";
  private static final String ALTERNATE_RECALL_RETURN_INTERVAL = "alternateRecallReturnInterval";
  private static final String FOR_USE_AT_LOCATION = "forUseAtLocation";

  private static final String INTERVAL_ID = "intervalId";
  private static final String DURATION = "duration";
  private static final String ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY = "alternateCheckoutLoanPeriod";
  private static final String GRACE_PERIOD_KEY = "gracePeriod";

  private static final String INTERVAL_ERROR_TEXT = "the interval \"%s\" in \"%s\" is not recognized";
  private static final String DURATION_ERROR_TEXT = "the duration \"%s\" in \"%s\" is invalid";

  @ToString.Include
  private final JsonObject representation;
  private final FixedDueDateSchedules fixedDueDateSchedules;
  private final FixedDueDateSchedules alternateRenewalFixedDueDateSchedules;
  private final AppliedRuleConditions ruleConditionsEntity;

  private LoanPolicy(JsonObject representation) {
    this(representation, new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(),
      new AppliedRuleConditions(false, false, false));
  }

  public LoanPolicy(
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

  public Result<ZonedDateTime> calculateInitialDueDate(Loan loan, RequestQueue requestQueue) {
    final ZonedDateTime systemTime = ClockUtil.getZonedDateTime();
    return determineStrategy(requestQueue, false, false, systemTime, loan.getItemId())
      .calculateDueDate(loan);
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

  public boolean hasReachedRenewalLimit(Loan loan) {
    if (isNotRenewable()) {
      return false;
    }

    return reachedNumberOfRenewalsLimit(loan) && !unlimitedRenewals();
  }

  public boolean allowRecallsToExtendOverdueLoans() {
   return getBooleanProperty(getRecalls(), ALLOW_RECALLS_TO_EXTEND_OVERDUE_LOANS);
  }

  public JsonObject getAlternateRecallReturnInterval() {
    return getObjectProperty(getRecalls(), ALTERNATE_RECALL_RETURN_INTERVAL);
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

  public DueDateStrategy determineStrategy(RequestQueue requestQueue, boolean isRenewal,
    boolean isRenewalWithHoldRequest, ZonedDateTime systemDate, String itemId) {

    final JsonObject loansPolicy = getLoansPolicy();
    final JsonObject renewalsPolicy = getRenewalsPolicy();
    final JsonObject holds = getHolds();

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
        boolean useAlternatePeriod = false;
        Period rollingPeriod = getPeriod(loansPolicy);
        if (isAlternatePeriod(requestQueue, itemId)) {
          rollingPeriod = getPeriod(holds, ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY);
          useAlternatePeriod = true;
        }

        return new RollingCheckOutDueDateStrategy(getId(), getName(),
          rollingPeriod, fixedDueDateSchedules, this::loanPolicyValidationError, useAlternatePeriod);
      }
    }
    else if(isFixed(loansPolicy)) {
      if (isRenewal) {
        return new FixedScheduleRenewalDueDateStrategy(getId(), getName(),
          getRenewalFixedDueDateSchedules(), systemDate, this::loanPolicyValidationError);
      }
      else {
        if (isAlternatePeriod(requestQueue, itemId)) {
          return new RollingCheckOutDueDateStrategy(getId(), getName(),
            getPeriod(holds, ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY),
              fixedDueDateSchedules, this::loanPolicyValidationError, false);
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

  private boolean isAlternatePeriod(RequestQueue requestQueue, String itemId) {
    if (Objects.isNull(requestQueue) || !getHolds().containsKey(
      ALTERNATE_CHECKOUT_LOAN_PERIOD_KEY)) {

      return false;
    }

    return requestQueue.getRequests().stream()
      .anyMatch(request -> request.getRequestType() == RequestType.HOLD &&
        request.getStatus() == RequestStatus.OPEN_NOT_YET_FILLED &&
        (!request.hasItem() || itemId.equals(request.getItemId())));
  }

  public JsonObject getLoansPolicy() {
    return representation.getJsonObject(LOANS_POLICY_KEY);
  }

  private JsonObject getRenewalsPolicy() {
    return representation.getJsonObject("renewalsPolicy");
  }

  private JsonObject getRequestManagement() {
    return getObjectProperty(representation, REQUEST_MANAGEMENT_KEY);
  }

  private JsonObject getRecalls() {
    return getObjectProperty(getRequestManagement(), "recalls");
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

  public LoanPolicy withDueDateSchedules(FixedDueDateSchedules loanSchedules) {
    return new LoanPolicy(representation, loanSchedules,
      alternateRenewalFixedDueDateSchedules, ruleConditionsEntity);
  }

  public LoanPolicy withDueDateSchedules(JsonObject fixedDueDateSchedules) {
    return withDueDateSchedules(FixedDueDateSchedules.from(fixedDueDateSchedules));
  }

  public LoanPolicy withAlternateRenewalSchedules(FixedDueDateSchedules renewalSchedules) {
    return new LoanPolicy(representation, fixedDueDateSchedules,
      renewalSchedules, ruleConditionsEntity);
  }

  public LoanPolicy withAlternateRenewalSchedules(JsonObject renewalSchedules) {
    return withAlternateRenewalSchedules(FixedDueDateSchedules.from(renewalSchedules));
  }

  public boolean isLoanable() {
    return getBooleanProperty(representation, "loanable");
  }

  public boolean isNotLoanable() {
    return !isLoanable();
  }

  public boolean isForUseAtLocation() {
    return getBooleanProperty(getLoansPolicy(), FOR_USE_AT_LOCATION);
  }

  public Period getHoldShelfExpiryPeriodForUseAtLocation() {
    if (isForUseAtLocation()) {
      JsonObject holdShelfExpiryPeriod = getObjectProperty(getLoansPolicy(), "holdShelfExpiryPeriodForUseAtLocation");
      if (holdShelfExpiryPeriod != null) {
        return Period.from(holdShelfExpiryPeriod);
      }
    }
    return null;
  }

  public TimePeriod getHoldShelfExpiryTimePeriodForUseAtLocation() {
    if (isForUseAtLocation()) {
      JsonObject holdShelfExpiryPeriod = getObjectProperty(getLoansPolicy(), "holdShelfExpiryPeriodForUseAtLocation");
      if (holdShelfExpiryPeriod != null) {
        return new TimePeriodMapper().toDomain(holdShelfExpiryPeriod);
      }
    }
    return null;
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

  public String getLoansFixedDueDateScheduleId() {
    return getProperty(getLoansPolicy(), "fixedDueDateScheduleId");
  }

  public String getAlternateRenewalsFixedDueDateScheduleId() {
    return getProperty(getRenewalsPolicy(), "alternateFixedDueDateScheduleId");
  }

  public Optional<ZonedDateTime> getScheduleLimit(ZonedDateTime loanDate, boolean isRenewal, ZonedDateTime systemDate) {
    final JsonObject loansPolicy = getLoansPolicy();

    if(loansPolicy == null) {
      return Optional.empty();
    }

    if(isRolling(loansPolicy)) {
      if(isRenewal) {
        return getRenewalDueDateLimitSchedules().findDueDateFor(systemDate);
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
        .getJsonObject(REQUEST_MANAGEMENT_KEY, new JsonObject())
        .getJsonObject("recalls", new JsonObject());

    final Result<ZonedDateTime> minimumDueDateResult =
        getDueDate("minimumGuaranteedLoanPeriod", recalls,
            loan.getLoanDate(), null);

    final ZonedDateTime systemDate = ClockUtil.getZonedDateTime();

    final Result<ZonedDateTime> recallDueDateResult =
        loan.isOverdue() &&
        allowRecallsToExtendOverdueLoans() &&
        getAlternateRecallReturnInterval() != null ?
        getDueDate("alternateRecallReturnInterval", recalls, systemDate, systemDate) :
        getDueDate("recallReturnInterval", recalls, systemDate, systemDate);

    final List<ValidationError> errors = new ArrayList<>();

    errors.addAll(combineValidationErrors(recallDueDateResult));
    errors.addAll(combineValidationErrors(minimumDueDateResult));

    if (errors.isEmpty()) {
      return determineDueDate(minimumDueDateResult, recallDueDateResult, loan)
          .map(dueDate -> changeDueDate(dueDate, loan));
    } else {
      return failedValidation(errors);
    }
  }

  private Result<ZonedDateTime> determineDueDate(Result<ZonedDateTime> minimumGuaranteedDueDateResult,
    Result<ZonedDateTime> recallDueDateResult, Loan loan) {

    return minimumGuaranteedDueDateResult.combine(recallDueDateResult,
      (minimumGuaranteedDueDate, recallDueDate) -> {
        log.debug("determineDueDate:: parameters minimumGuaranteedDueDateResult: {}, " +
            "recallDueDateResult: {}, loan: {}", () -> resultAsString(minimumGuaranteedDueDateResult),
          () -> resultAsString(recallDueDateResult), () -> loan);

        ZonedDateTime currentDueDate = loan.getDueDate();

        if (loan.isOverdue() && !allowRecallsToExtendOverdueLoans()) {
          log.info("determineDueDate:: loan is overdue and allowRecallsToExtendOverdueLoans is " +
            "disabled - keeping current due date");
          return currentDueDate;
        }

        if (isAfterMillis(recallDueDate, currentDueDate) && !allowRecallsToExtendOverdueLoans()) {
          log.info("determineDueDate:: current due date is before recall due date and " +
            "allowRecallsToExtendOverdueLoans is disabled - keeping current due date");
          return currentDueDate;
        } else {
          if (minimumGuaranteedDueDate == null ||
            isAfterMillis(recallDueDate, minimumGuaranteedDueDate)) {

            log.info("determineDueDate:: minimum guaranteed period doesn't exist or recall due " +
              "date is after minimum guaranteed due date - changing due date to recall due date");
            return recallDueDate;
          } else {
            log.info("determineDueDate:: minimum guaranteed period exists and recall due " +
              "date is before minimum guaranteed due date - changing due date to minimum " +
              "guaranteed due date");
            return minimumGuaranteedDueDate;
          }
        }
      });
  }

  private Loan changeDueDate(ZonedDateTime dueDate, Loan loan) {
    if (!loan.wasDueDateChangedByRecall()) {
      loan.changeDueDate(dueDate);
      loan.resetReminders();
      loan.setDueDateChangedByRecall();
    }

    return loan;
  }

  private Result<ZonedDateTime> getDueDate(
    String key,
    JsonObject representation,
    ZonedDateTime initialDateTime,
    ZonedDateTime defaultDateTime) {

    final Result<ZonedDateTime> result;

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
