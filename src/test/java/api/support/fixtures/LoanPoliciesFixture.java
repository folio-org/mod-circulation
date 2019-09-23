package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
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

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    loanPolicyRecordCreator.cleanUp();
    fixedDueDateScheduleRecordCreator.cleanUp();
  }

  public IndividualResource createExampleFixedDueDateSchedule()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder fixedDueDateSchedule =
      new FixedDueDateSchedulesBuilder()
        .withName("Example Fixed Due Date Schedule")
        .withDescription("Example Fixed Due Date Schedule")
        .addSchedule(new FixedDueDateSchedule(
          new DateTime(2019, 1, 1, 0, 0, 0, DateTimeZone.UTC),
          new DateTime(2019, 12, 31, 23, 59, 59, DateTimeZone.UTC),
          new DateTime(2019, 12, 31, 23, 59, 59, DateTimeZone.UTC)
        ));

    return createSchedule(fixedDueDateSchedule);
  }

  public IndividualResource create(LoanPolicyBuilder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    return loanPolicyRecordCreator.createIfAbsent(builder);
  }

  public IndividualResource create(JsonObject policy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return loanPolicyRecordCreator.createIfAbsent(policy);
  }

  public IndividualResource createSchedule(FixedDueDateSchedulesBuilder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return fixedDueDateScheduleRecordCreator.createIfAbsent(builder);
  }

  public IndividualResource canCirculateRolling()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
      ExecutionException {

    JsonObject holds = new JsonObject();
    holds.put("alternateRenewalLoanPeriod", Period.weeks(3).asJson());
    holds.put("renewItemsWithRequest", true);

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling")
      .withDescription("Can circulate item")
      .withHolds(holds)
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate();

    return loanPolicyRecordCreator.createIfAbsent(canCirculateRollingPolicy);
  }

  public IndividualResource canCirculateFixed()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
