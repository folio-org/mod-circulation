package api.support.fixtures;

import static api.support.http.ResourceClient.forOverdueFinePolicies;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;

public class OverdueFinePoliciesFixture {
  private final RecordCreator overdueFinePolicyRecordCreator;

  public OverdueFinePoliciesFixture() {
    overdueFinePolicyRecordCreator = new RecordCreator(forOverdueFinePolicies(),
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {
    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandardPolicy());
  }

  public OverdueFinePolicyBuilder facultyStandardPolicy() {
    return new OverdueFinePolicyBuilder()
      .withName("Faculty standard")
      .withDescription("This is description for Faculty standard")
      .withOverdueFine(5, "day")
      .withCountClosed(true)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(1, "hour")
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);
  }

  public IndividualResource facultyStandardShouldForgiveFine() {
    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
      .withName("Faculty standard (should forgive overdue fine for renewals)")
      .withDescription("This is description for Faculty standard " +
        "(should forgive overdue fine for renewals)")
      .withOverdueFine(5, "day")
      .withCountClosed(true)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(true)
      .withOverdueRecallFine(1, "hour")
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource facultyStandardDoNotCountClosed() {
    final OverdueFinePolicyBuilder overdueFinePolicyBuilder = new OverdueFinePolicyBuilder()
      .withName("Faculty standard (don't count closed)")
      .withDescription("This is description for Faculty standard (don't count closed)")
      .withOverdueFine(5, "day")
      .withCountClosed(false)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(1, "hour")
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicyBuilder);
  }

  public IndividualResource  noOverdueFine() {
    JsonObject overdueFinePolicy = new JsonObject();
    overdueFinePolicy.put("id", UUID.randomUUID().toString());
    overdueFinePolicy.put("name", "No overdue fine policy");
    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicy);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy) {
    return overdueFinePolicyRecordCreator.createIfAbsent(noticePolicy);
  }

  public IndividualResource create(OverdueFinePolicyBuilder overdueFinePolicyBuilder) {
    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicyBuilder);
  }

  public void cleanUp() {
    overdueFinePolicyRecordCreator.cleanUp();
  }
}
