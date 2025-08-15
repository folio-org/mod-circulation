package api.support.builders;

import java.util.Objects;
import java.util.UUID;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class LoanPolicyBuilder extends JsonBuilder implements Builder {
  private static final String RENEW_FROM_SYSTEM_DATE = "SYSTEM_DATE";
  private static final String RENEW_FROM_DUE_DATE = "CURRENT_DUE_DATE";

  private final UUID id;
  private final String name;
  private final String description;
  private final String loansProfile;
  private final Period loanPeriod;
  private final UUID fixedDueDateScheduleId;
  private final boolean unlimitedRenewals;
  private final Integer numberAllowed;
  private final String renewFrom;
  private final boolean renewWithDifferentPeriod;
  private final Period differentRenewalPeriod;
  private final UUID alternateFixedDueDateScheduleId;
  private final String closedLibraryDueDateManagementId;
  private final Period openingTimeOffsetPeriod;
  private final boolean renewable;
  private final boolean loanable;
  private final Period recallsMinimumGuaranteedLoanPeriod;
  private final Period recallsRecallReturnInterval;
  private final Period alternateRecallReturnInterval;
  private final boolean allowRecallsToExtendOverdueLoans;
  private final JsonObject holds;
  private final Period alternateCheckoutLoanPeriod;
  private final Integer itemLimit;
  private final Period gracePeriod;
  private final boolean forUseAtLocation;
  private final Period holdShelfExpiryPeriodForUseAtLocation;

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
      "CURRENT_DUE_DATE_TIME",
      null,
      true,
      true,
      null,
      null,
      null,
      false,
      null,
      null,
      null,
      null,
      false,
      null
    );
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

      put(loansPolicy, "profileId", loansProfile);
      put(loansPolicy, "itemLimit", itemLimit);
      putIfNotNull(loansPolicy, "gracePeriod", gracePeriod, Period::asJson);
      put(loansPolicy, "forUseAtLocation", forUseAtLocation);
      putIfNotNull(loansPolicy, "holdShelfExpiryPeriodForUseAtLocation", holdShelfExpiryPeriodForUseAtLocation, Period::asJson);

      //TODO: Replace with sub-builders
      if(Objects.equals(loansProfile, "Rolling")) {
        putIfNotNull(loansPolicy, "period", loanPeriod, Period::asJson);

        //Due date limited rolling policy, maybe should be separate property
        put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

        put(loansPolicy, "closedLibraryDueDateManagementId",
          closedLibraryDueDateManagementId);

        putIfNotNull(loansPolicy, "openingTimeOffset", openingTimeOffsetPeriod,
          Period::asJson);
      }
      else if(Objects.equals(loansProfile, "Fixed")) {
        put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

        put(loansPolicy, "closedLibraryDueDateManagementId",
          closedLibraryDueDateManagementId);

        putIfNotNull(loansPolicy, "openingTimeOffset", openingTimeOffsetPeriod,
          Period::asJson);
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
        putIfNotNull(renewalsPolicy, "period", differentRenewalPeriod,
          Period::asJson);

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
      putIfNotNull(recalls, "minimumGuaranteedLoanPeriod", recallsMinimumGuaranteedLoanPeriod,
        Period::asJson);
    }

    if (recallsRecallReturnInterval != null) {
      if (recalls == null) {
        recalls = new JsonObject();
      }
      putIfNotNull(recalls, "recallReturnInterval", recallsRecallReturnInterval,
        Period::asJson);
    }

    if (alternateRecallReturnInterval != null) {
      if (recalls == null) {
        recalls = new JsonObject();
      }
      putIfNotNull(recalls, "alternateRecallReturnInterval", alternateRecallReturnInterval,
        Period::asJson);
    }

    if (recalls != null) {
      put(recalls, "allowRecallsToExtendOverdueLoans", allowRecallsToExtendOverdueLoans);
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
      putIfNotNull(newHolds, "alternateCheckoutLoanPeriod", alternateCheckoutLoanPeriod,
        Period::asJson);
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

  public LoanPolicy asDomainObject() {
    return LoanPolicy.from(create());
  }

  public LoanPolicyBuilder rolling(Period period) {
    return withLoansProfile("Rolling").withLoanPeriod(period);
  }

  public LoanPolicyBuilder fixed(UUID fixedDueDateScheduleId) {
    return withLoansProfile("Fixed").withFixedDueDateScheduleId(fixedDueDateScheduleId)
      .renewFromSystemDate();
  }

  public LoanPolicyBuilder limitedBySchedule(UUID fixedDueDateScheduleId) {
    if(!Objects.equals(this.loansProfile, "Rolling")) {
      throw new IllegalArgumentException(
        "Cannot apply due date limit if not rolling policy");
    }

    return withFixedDueDateScheduleId(fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder unlimitedRenewals() {
    return withUnlimitedRenewals(true).withNumberAllowed(null);
  }

  public LoanPolicyBuilder limitedRenewals(int limit) {
    return withUnlimitedRenewals(false).withNumberAllowed(limit);
  }

  public LoanPolicyBuilder renewFromSystemDate() {
    return renewFrom(RENEW_FROM_SYSTEM_DATE);
  }

  public LoanPolicyBuilder renewFromCurrentDueDate() {
    return renewFrom(RENEW_FROM_DUE_DATE);
  }

  private LoanPolicyBuilder renewFrom(String renewFrom) {
    return withRenewFrom(renewFrom);
  }

  public LoanPolicyBuilder renewWith(Period period) {
    return renewWith(period, null);
  }

  public LoanPolicyBuilder renewWith(Period period, UUID dueDateLimitScheduleId) {
    return withRenewWithDifferentPeriod(true)
      .withDifferentRenewalPeriod(period)
      .withAlternateFixedDueDateScheduleId(dueDateLimitScheduleId);
  }

  public LoanPolicyBuilder renewWith(UUID fixedDueDateScheduleId) {
    if(!Objects.equals(loansProfile, "Fixed")) {
      throw new IllegalArgumentException("Can only be used with fixed profile");
    }
    return renew(fixedDueDateScheduleId);
  }

  private LoanPolicyBuilder renew(UUID fixedDueDateScheduleId) {
    return withRenewWithDifferentPeriod(true)
      .withDifferentRenewalPeriod(null)
      .withAlternateFixedDueDateScheduleId(fixedDueDateScheduleId);
  }

  public LoanPolicyBuilder notRenewable() {
    return withRenewable(false);
  }

  public LoanPolicyBuilder withClosedLibraryDueDateManagement(
    String closedLibraryDueDateManagementId) {

    return withClosedLibraryDueDateManagementId(closedLibraryDueDateManagementId);
  }

  public LoanPolicyBuilder withOpeningTimeOffset(Period openingTimeOffsetPeriod) {
    return withOpeningTimeOffsetPeriod(openingTimeOffsetPeriod);
  }

  public LoanPolicyBuilder withHolds(Period checkoutPeriod,
    boolean renewable, Period renewalPeriod) {

    final var json = new JsonObject();
    putIfNotNull(json, "alternateCheckoutLoanPeriod", checkoutPeriod, Period::asJson);
    put(json, "renewItemsWithRequest", renewable);
    putIfNotNull(json, "alternateRenewalLoanPeriod", renewalPeriod, Period::asJson);

    return withHolds(json);
  }

}
