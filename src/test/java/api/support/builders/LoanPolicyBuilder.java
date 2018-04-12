package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.Objects;
import java.util.UUID;

public class LoanPolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final String profile;
  private final JsonObject loanPeriod;
  private final UUID fixedDueDateScheduleId;

  public LoanPolicyBuilder() {
    this(null, "Example Loan Policy", "An example loan policy", null, null,
      null);
  }

  private LoanPolicyBuilder(
    UUID id,
    String name,
    String description,
    String profile,
    JsonObject loanPeriod,
    UUID fixedDueDateScheduleId) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.profile = profile;
    this.loanPeriod = loanPeriod;
    this.fixedDueDateScheduleId = fixedDueDateScheduleId;
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
    }
    else if(Objects.equals(profile, "Fixed")) {
      loansPolicy.put("fixedDueDateScheduleId", fixedDueDateScheduleId);
    }
    
    loansPolicy.put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE");

    request.put("loansPolicy", loansPolicy);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", true);
    renewalsPolicy.put("renewFromId", "CURRENT_DUE_DATE");
    renewalsPolicy.put("differentPeriod", false);

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
      this.fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder withName(String name) {
    return new LoanPolicyBuilder(
      this.id,
      name,
      this.description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder withDescription(String description) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      this.profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder withLoansProfile(String profile) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      profile,
      this.loanPeriod,
      this.fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder rolling(Period period) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      "Rolling",
      createPeriod(period),
      null);
  }

  public LoanPolicyBuilder fixed(UUID fixedDueDateScheduleId) {
    return new LoanPolicyBuilder(
      this.id,
      this.name,
      description,
      "Fixed",
      null,
      fixedDueDateScheduleId);
  }

  private static JsonObject createPeriod(Period period) {
    JsonObject representation = new JsonObject();

    representation.put("duration", period.duration);
    representation.put("intervalId", period.interval);

    return representation;
  }
}
