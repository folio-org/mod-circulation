package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.policy.Period;

import api.support.http.IndividualResource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanPoliciesFixture {
  private final RecordCreator loanPolicyRecordCreator;
  private final RecordCreator fixedDueDateScheduleRecordCreator;

  public LoanPoliciesFixture(
    ResourceClient loanPoliciesClient,
    ResourceClient fixedDueDateScheduleClient) {

    loanPolicyRecordCreator = new RecordCreator(loanPoliciesClient,
      reason -> getProperty(reason, "name"));

    fixedDueDateScheduleRecordCreator = new RecordCreator(
      fixedDueDateScheduleClient, schedule -> getProperty(schedule, "name"));
  }

  public void cleanUp() {
    loanPolicyRecordCreator.cleanUp();
    fixedDueDateScheduleRecordCreator.cleanUp();
  }

  public void delete(IndividualResource record) {
    loanPolicyRecordCreator.delete(record);
  }

  public IndividualResource createExampleFixedDueDateSchedule() {
    int currentYear = DateTime.now(DateTimeZone.UTC).getYear();
    return createExampleFixedDueDateSchedule(currentYear,
      new DateTime(currentYear, 12, 31, 23, 59, 59, DateTimeZone.UTC));
  }

  public IndividualResource createExampleFixedDueDateSchedule(int year, DateTime dueDate) {
    FixedDueDateSchedulesBuilder fixedDueDateSchedule =
      new FixedDueDateSchedulesBuilder()
        .withName("Example Fixed Due Date Schedule")
        .withDescription("Example Fixed Due Date Schedule")
        .addSchedule(new FixedDueDateSchedule(
          new DateTime(year, 1, 1, 0, 0, 0, DateTimeZone.UTC),
          new DateTime(year, 12, 31, 23, 59, 59, DateTimeZone.UTC),
          dueDate));

    return createSchedule(fixedDueDateSchedule);
  }

  public IndividualResource create(LoanPolicyBuilder builder) {
    return loanPolicyRecordCreator.createIfAbsent(builder);
  }

  public IndividualResource create(JsonObject policy) {
    return loanPolicyRecordCreator.createIfAbsent(policy);
  }

  public IndividualResource createSchedule(FixedDueDateSchedulesBuilder builder) {
    return fixedDueDateScheduleRecordCreator.createIfAbsent(builder);
  }

  public IndividualResource canCirculateRolling(Period gracePeriod) {
    JsonObject holds = new JsonObject();
    holds.put("alternateRenewalLoanPeriod", Period.weeks(3).asJson());
    holds.put("renewItemsWithRequest", true);

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling")
      .withDescription("Can circulate item")
      .withHolds(holds)
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .withGracePeriod(gracePeriod)
      .renewFromSystemDate();

    return loanPolicyRecordCreator.createIfAbsent(canCirculateRollingPolicy);
  }

  public IndividualResource canCirculateRolling() {
    return canCirculateRolling(null);
  }

  public IndividualResource canCirculateFixed() {
    JsonObject holds = new JsonObject();
    holds.put("alternateRenewalLoanPeriod", Period.weeks(3).asJson());
    holds.put("renewItemsWithRequest", true);

    LoanPolicyBuilder canCirculateFixedLoanPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Fixed")
      .withHolds(holds)
      .withDescription("Can circulate item")
      .fixed(createExampleFixedDueDateSchedule().getId());

    return loanPolicyRecordCreator.createIfAbsent(canCirculateFixedLoanPolicy);
  }
}
