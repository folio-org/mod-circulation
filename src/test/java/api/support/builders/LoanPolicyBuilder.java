package api.support.builders;

import java.util.Objects;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;

public class LoanPolicyBuilder extends JsonBuilder implements Builder {
  private static final String RENEW_FROM_SYSTEM_DATE = "SYSTEM_DATE";
  private static final String RENEW_FROM_DUE_DATE = "CURRENT_DUE_DATE";

  private final UUID id;
  private final String name;
  private final String description;
  private final String profile;
  private final JsonObject loanPeriod;
  private final UUID fixedDueDateScheduleId;
  private final boolean unlimitedRenewals;
  private final Integer numberAllowed;
  private final String renewFrom;
  private final boolean renewWithDifferentPeriod;
  private final JsonObject differentRenewalPeriod;
  private final UUID alternateFixedDueDateScheduleId;
  private final String closedLibraryDueDateManagementId;
  private final JsonObject openingTimeOffsetPeriod;
  private final boolean renewable;
  private final boolean loanable;
  private final JsonObject recallsMinimumGuaranteedLoanPeriod;
  private final JsonObject recallsRecallReturnInterval;
  private final JsonObject holds;
  private final JsonObject alternateCheckoutLoanPeriod;
  private final Integer itemLimit;
  private final JsonObject gracePeriod;

  public LoanPolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      true,
      null,
      null,
      null,
      "CURRENT_DUE_DATE_TIME",
      null,
      true,
      null,
      RENEW_FROM_DUE_DATE,
      false,
      null,
      null,
      true,
      null,
      null,
      null,
      null,
      null,
      null
    );
  }

  private LoanPolicyBuilder(
    UUID id,
    String name,
    String description,
    boolean loanable,
    String profile,
    JsonObject loanPeriod,
    UUID fixedDueDateScheduleId,
    String closedLibraryDueDateManagementId,
    JsonObject openingTimeOffsetPeriod,
    boolean unlimitedRenewals,
    Integer numberAllowed, String renewFrom,
    boolean renewWithDifferentPeriod,
    JsonObject differentRenewalPeriod,
    UUID alternateFixedDueDateScheduleId,
    boolean renewable,
    JsonObject recallsMinimumGuaranteedLoanPeriod,
    JsonObject recallsRecallReturnInterval,
    JsonObject holds,
    JsonObject alternateCheckoutLoanPeriod,
    Integer itemLimit,
    JsonObject gracePeriod) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.loanable = loanable;
    this.profile = profile;
    this.loanPeriod = loanPeriod;
    this.fixedDueDateScheduleId = fixedDueDateScheduleId;

    this.closedLibraryDueDateManagementId = closedLibraryDueDateManagementId;
    this.openingTimeOffsetPeriod = openingTimeOffsetPeriod;

    this.unlimitedRenewals = unlimitedRenewals;
    this.renewFrom = renewFrom;
    this.renewWithDifferentPeriod = renewWithDifferentPeriod;
    this.differentRenewalPeriod = differentRenewalPeriod;
    this.alternateFixedDueDateScheduleId = alternateFixedDueDateScheduleId;
    this.numberAllowed = numberAllowed;
    this.renewable = renewable;
    this.recallsMinimumGuaranteedLoanPeriod = recallsMinimumGuaranteedLoanPeriod;
    this.recallsRecallReturnInterval = recallsRecallReturnInterval;
    this.holds = holds;
    this.alternateCheckoutLoanPeriod = alternateCheckoutLoanPeriod;
    this.itemLimit = itemLimit;
    this.gracePeriod = gracePeriod;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      put(request, "id", id.toString());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "loanable", loanable);
    put(request, "renewable", renewable);

    if(loanable) {
      JsonObject loansPolicy = new JsonObject();

      put(loansPolicy, "profileId", profile);
      put(loansPolicy, "itemLimit", itemLimit);
      put(loansPolicy, "gracePeriod", gracePeriod);

      //TODO: Replace with sub-builders
      if(Objects.equals(profile, "Rolling")) {
        put(loansPolicy, "period", loanPeriod);

        //Due date limited rolling policy, maybe should be separate property
        put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

        put(loansPolicy, "closedLibraryDueDateManagementId",
          closedLibraryDueDateManagementId);

        put(loansPolicy, "openingTimeOffset", openingTimeOffsetPeriod);
      }
      else if(Objects.equals(profile, "Fixed")) {
        put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

        put(loansPolicy, "closedLibraryDueDateManagementId",
          closedLibraryDueDateManagementId);

        put(loansPolicy, "openingTimeOffset", openingTimeOffsetPeriod);
      }

      put(request, "loansPolicy", loansPolicy);
    }

    if(renewable) {
      JsonObject renewalsPolicy = new JsonObject();

      put(renewalsPolicy, "unlimited", unlimitedRenewals);

      if(!unlimitedRenewals) {
        put(renewalsPolicy, "numberAllowed", numberAllowed);
      }

      put(renewalsPolicy, "renewFromId", renewFrom);
      put(renewalsPolicy, "differentPeriod", renewWithDifferentPeriod);

      if(renewWithDifferentPeriod) {
        put(renewalsPolicy, "period", differentRenewalPeriod);

        if(alternateFixedDueDateScheduleId != null) {
          put(renewalsPolicy, "alternateFixedDueDateScheduleId",
            alternateFixedDueDateScheduleId);
        }
      }

      put(request, "renewalsPolicy", renewalsPolicy);
    }

    JsonObject recalls = null;

    if (recallsMinimumGuaranteedLoanPeriod != null) {
      recalls = new JsonObject();
      put(recalls, "minimumGuaranteedLoanPeriod", recallsMinimumGuaranteedLoanPeriod);
    }

    if (recallsRecallReturnInterval != null) {
      if (recalls == null) {
        recalls = new JsonObject();
      }
      put(recalls, "recallReturnInterval", recallsRecallReturnInterval);
    }

    JsonObject requestManagement = null;

    if (recalls != null) {
      requestManagement = new JsonObject();
      put(requestManagement, "recalls", recalls);
    }

    JsonObject newHolds = null;
    if (alternateCheckoutLoanPeriod != null) {
      if (requestManagement == null) {
        requestManagement = new JsonObject();
      }
      newHolds = new JsonObject();
      put(newHolds, "alternateCheckoutLoanPeriod", alternateCheckoutLoanPeriod);
      put(requestManagement, "holds", newHolds);
    }

    if (holds != null) {
      if (requestManagement == null) {
        requestManagement = new JsonObject();
      }
      put(requestManagement, "holds", holds);
    }

    if (requestManagement != null) {
      put(request, "requestManagement", requestManagement);
    }

    return request;
  }

  public LoanPolicyBuilder withId(UUID id) {
    return new LoanPolicyBuilder(
      id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withName(String name) {
    return new LoanPolicyBuilder(
      this.id,
      name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withDescription(String description) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withLoanable(boolean loanable) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withLoanPeriod(JsonObject loanPeriod) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withLoansProfile(String profile) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder rolling(Period period) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      "Rolling",
      period.asJson(),
      null,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder fixed(UUID fixedDueDateScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      "Fixed",
      null,
      fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      RENEW_FROM_SYSTEM_DATE,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder limitedBySchedule(UUID fixedDueDateScheduleId) {
    if(!Objects.equals(this.profile, "Rolling")) {
      throw new IllegalArgumentException(
        "Cannot apply due date limit if not rolling policy");
    }

    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder unlimitedRenewals() {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, true,
      null,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder limitedRenewals(int limit) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, false,
      limit,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder renewFromSystemDate() {
    return renewFrom(RENEW_FROM_SYSTEM_DATE);
  }

  public LoanPolicyBuilder renewFromCurrentDueDate() {
    return renewFrom(RENEW_FROM_DUE_DATE);
  }

  private LoanPolicyBuilder renewFrom(String renewFrom) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder renewWith(Period period) {
    return renewWith(period, null);
  }

  public LoanPolicyBuilder renewWith(Period period, UUID dueDateLimitScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      true,
      period.asJson(),
      dueDateLimitScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder renewWith(UUID fixedDueDateScheduleId) {
    if(!Objects.equals(profile, "Fixed")) {
      throw new IllegalArgumentException("Can only be used with fixed profile");
    }
    return renew(fixedDueDateScheduleId);
  }

  private LoanPolicyBuilder renew(UUID fixedDueDateScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      true,
      null,
      fixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder notRenewable() {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      false,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withClosedLibraryDueDateManagement(
    String closedLibraryDueDateManagementId) {

    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod, this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withOpeningTimeOffset(Period openingTimeOffsetPeriod) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      openingTimeOffsetPeriod.asJson(),
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withRecallsMinimumGuaranteedLoanPeriod(Period recallsMinimumGuaranteedLoanPeriod) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      recallsMinimumGuaranteedLoanPeriod.asJson(),
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withRecallsRecallReturnInterval(Period recallsRecallReturnInterval) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      recallsRecallReturnInterval.asJson(),
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withHolds(JsonObject holds) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withAlternateCheckoutLoanPeriod(Period alternateCheckoutLoanPeriod) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      alternateCheckoutLoanPeriod.asJson(),
      this.itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withItemLimit(Integer itemLimit) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      itemLimit,
      this.gracePeriod);
  }

  public LoanPolicyBuilder withGracePeriod(Period gracePeriod) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.loanable,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.closedLibraryDueDateManagementId,
      this.openingTimeOffsetPeriod,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable,
      this.recallsMinimumGuaranteedLoanPeriod,
      this.recallsRecallReturnInterval,
      this.holds,
      this.alternateCheckoutLoanPeriod,
      this.itemLimit,
      gracePeriod.asJson());
  }
}
