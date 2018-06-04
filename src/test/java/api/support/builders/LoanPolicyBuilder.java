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
  private final String renewFrom;
  private final boolean renewWithDifferentPeriod;
  private final JsonObject differentRenewalPeriod;

  public LoanPolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      null,
      null,
      null,
      true,
      RENEW_FROM_DUE_DATE,
      false,
      null);
  }

  private LoanPolicyBuilder(
    UUID id,
    String name,
    String description,
    String profile,
    JsonObject loanPeriod,
    UUID fixedDueDateScheduleId,
    boolean unlimitedRenewals,
    String renewFrom,
    boolean renewWithDifferentPeriod,
    JsonObject differentRenewalPeriod) {

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
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      request.put("id", id.toString());
    }

    request.put("name", this.name);
    request.put("description", this.description);
    request.put("loanable", true);
    request.put("renewable", true);

    JsonObject loansPolicy = new JsonObject();

    loansPolicy.put("profileId", profile);

    //TODO: Replace with sub-builders
    if(Objects.equals(profile, "Rolling")) {
      loansPolicy.put("period", loanPeriod);

      //Due date limited rolling policy, maybe should be separate property
      put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);
    }
    else if(Objects.equals(profile, "Fixed")) {
      put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);
    }

    loansPolicy.put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE");

    request.put("loansPolicy", loansPolicy);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", unlimitedRenewals);
    renewalsPolicy.put("renewFromId", renewFrom);
    renewalsPolicy.put("differentPeriod", renewWithDifferentPeriod);
    if(renewWithDifferentPeriod) {
      renewalsPolicy.put("period", differentRenewalPeriod);
    }

    request.put("renewalsPolicy", renewalsPolicy);

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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      this.renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
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
      renewFrom,
      this.renewWithDifferentPeriod,
      this.differentRenewalPeriod);
  }

  public LoanPolicyBuilder renewWith(Period period) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId,
      this.unlimitedRenewals,
      renewFrom,
      true,
      period.asJson());
  }
}
