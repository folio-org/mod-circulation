package api.support.fixtures;

import static api.support.http.ResourceClient.forOverdueFinePolicies;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import api.support.builders.OverdueFinePolicyWithReminderFeesBuilder;
import api.support.http.IndividualResource;

import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;

public class OverdueFinePoliciesFixture {
  private final RecordCreator overdueFinePolicyRecordCreator;
  public final UUID FIRST_REMINDER_TEMPLATE_ID = UUID.randomUUID();
  public final UUID SECOND_REMINDER_TEMPLATE_ID = UUID.randomUUID();
  public final UUID THIRD_REMINDER_TEMPLATE_ID = UUID.randomUUID();


  public OverdueFinePoliciesFixture() {
    overdueFinePolicyRecordCreator = new RecordCreator(forOverdueFinePolicies(),
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {

    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");


    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
      .withName("Faculty standard")
      .withDescription("This is description for Faculty standard")
      .withOverdueFine(overdueFine)
      .withCountClosed(true)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(overdueRecallFine)
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource facultyStandardShouldForgiveFine() {

    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");


    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
        .withName("Faculty standard (should forgive overdue fine for renewals)")
        .withDescription("This is description for Faculty standard (should forgive overdue fine for renewals)")
        .withOverdueFine(overdueFine)
        .withCountClosed(true)
        .withMaxOverdueFine(50.00)
        .withForgiveOverdueFine(true)
        .withOverdueRecallFine(overdueRecallFine)
        .withGracePeriodRecall(false)
        .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource facultyStandardDoNotCountClosed() {
    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");

    final OverdueFinePolicyBuilder overdueFinePolicyBuilder = new OverdueFinePolicyBuilder()
      .withName("Faculty standard (don't count closed)")
      .withDescription("This is description for Faculty standard (don't count closed)")
      .withOverdueFine(overdueFine)
      .withCountClosed(false)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(overdueRecallFine)
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

  public IndividualResource remindersTwoDaysBetween(boolean canScheduleRemindersOnClosedDays) {
    OverdueFinePolicyWithReminderFeesBuilder policy = new OverdueFinePolicyWithReminderFeesBuilder(
      UUID.randomUUID(),
      "Reminder fee policy 1-2-2: schedule on closed: " + canScheduleRemindersOnClosedDays)
      .withAddedReminderEntry(
        1,"day",1.50,
        "Email",FIRST_REMINDER_TEMPLATE_ID.toString())
      .withAddedReminderEntry(2, "day", 0.00,
        "Email", SECOND_REMINDER_TEMPLATE_ID.toString())
      .withAddedReminderEntry(2,"day", 2.15,
        "Email", THIRD_REMINDER_TEMPLATE_ID.toString())
      .withCanSendReminderUponClosedDay(canScheduleRemindersOnClosedDays);
    return overdueFinePolicyRecordCreator.createIfAbsent(policy.create());
  }

  public IndividualResource remindersOneDayBetween(boolean canScheduleRemindersOnClosedDays) {
    OverdueFinePolicyWithReminderFeesBuilder policy = new OverdueFinePolicyWithReminderFeesBuilder(
      UUID.randomUUID(),
      "Reminder fee policy 0-1-1: send on closed " + canScheduleRemindersOnClosedDays)
      .withAddedReminderEntry(
        0,"day",1.50,
        "Email",FIRST_REMINDER_TEMPLATE_ID.toString())
      .withAddedReminderEntry(1, "day", 0.00,
        "Email", SECOND_REMINDER_TEMPLATE_ID.toString())
      .withAddedReminderEntry(1,"day", 2.15,
        "Print", THIRD_REMINDER_TEMPLATE_ID.toString())
      .withCanSendReminderUponClosedDay(canScheduleRemindersOnClosedDays)
      .withAllowRenewalOfItemsWithReminderFees(true);
    return overdueFinePolicyRecordCreator.createIfAbsent(policy.create());
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
