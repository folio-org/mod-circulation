package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.Period;

import java.util.Objects;
import java.util.UUID;

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
  private final boolean renewable;

  public LoanPolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      null,
      null,
      null,
      true,
      null,
      RENEW_FROM_DUE_DATE,
      false,
      null,
      null,
      true
    );
  }

  private LoanPolicyBuilder(
    UUID id,
    String name,
    String description,
    String profile,
    JsonObject loanPeriod,
    UUID fixedDueDateScheduleId,
    boolean unlimitedRenewals,
    Integer numberAllowed, String renewFrom,
    boolean renewWithDifferentPeriod,
    JsonObject differentRenewalPeriod,
    UUID alternateFixedDueDateScheduleId,
    boolean renewable) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.profile = profile;
    this.loanPeriod = loanPeriod;
    this.fixedDueDateScheduleId = fixedDueDateScheduleId;
    this.unlimitedRenewals = unlimitedRenewals;
    this.renewFrom = renewFrom;
    this.renewWithDifferentPeriod = renewWithDifferentPeriod;
    this.differentRenewalPeriod = differentRenewalPeriod;
    this.alternateFixedDueDateScheduleId = alternateFixedDueDateScheduleId;
    this.numberAllowed = numberAllowed;
    this.renewable = renewable;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      put(request, "id", id.toString());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "loanable", true);
    put(request, "renewable", renewable);

    JsonObject loansPolicy = new JsonObject();

    put(loansPolicy, "profileId", profile);

    //TODO: Replace with sub-builders
    if(Objects.equals(profile, "Rolling")) {
      put(loansPolicy, "period", loanPeriod);

      //Due date limited rolling policy, maybe should be separate property
      put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);
    }
    else if(Objects.equals(profile, "Fixed")) {
      put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);
    }

    put(loansPolicy, "closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE");

    put(request, "loansPolicy", loansPolicy);

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

    return request;
  }

  public LoanPolicyBuilder withId(UUID id) {
    return new LoanPolicyBuilder(
      id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder withName(String name) {
    return new LoanPolicyBuilder(
      this.id,
      name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder withDescription(String description) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder withLoansProfile(String profile) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder rolling(Period period) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      "Rolling",
      period.asJson(),
      null,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder fixed(UUID fixedDueDateScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      "Fixed",
      null,
      fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      RENEW_FROM_SYSTEM_DATE,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
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
      this.profile,
      this.loanPeriod,
      fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder unlimitedRenewals() {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      true,
      null,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder limitedRenewals(int limit) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      false,
      limit,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
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
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder renewWith(Period period) {
    return renewWith(period, null);
  }

  public LoanPolicyBuilder renewWith(Period period, UUID dueDateLimitScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      true,
      period.asJson(),
      dueDateLimitScheduleId,
      this.renewable);
  }

  public Builder renewWith(UUID fixedDueDateScheduleId) {
    if(!Objects.equals(profile, "Fixed")) {
      throw new IllegalArgumentException("Can only be used with fixed profile");
    }

    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      true,
      null,
      fixedDueDateScheduleId,
      this.renewable);
  }

  public LoanPolicyBuilder notRenewable() {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      this.numberAllowed,
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod,
      this.alternateFixedDueDateScheduleId,
      false);
  }
}
