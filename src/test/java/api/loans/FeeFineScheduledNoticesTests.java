package api.loans;

import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.OVERDUE_FINE_RETURNED;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticesTests extends APITests {
  @Test
  public void overdueFineNoticesAreScheduledOnCheckinWhenConfiguredInPatronNoticePolicy() {
    UUID uponAtTemplateId = randomUUID();
    UUID afterTemplateId = randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

    JsonObject uponAtNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(uponAtTemplateId)
      .withOverdueFineReturnedEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    JsonObject afterNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(afterTemplateId)
      .withOverdueFineReturnedEvent()
      .withAfterTiming(afterPeriod)
      .recurring(afterRecurringPeriod)
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder patronNoticePolicy = new NoticePolicyBuilder()
      .withName("Test policy")
      .withFeeFineNotices(Arrays.asList(
        uponAtNoticeConfiguration,
        afterNoticeConfiguration));

    use(patronNoticePolicy);

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    loansFixture.checkOutByBarcode(nod, james,
      new DateTime(2020, 1, 1, 12, 0, 0, DateTimeZone.UTC));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    feeFinesClient.create(new FeeFineBuilder()
      .withId(randomUUID())
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
    );

    CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(new DateTime(2020, 1, 25, 12, 0, 0, DateTimeZone.UTC))
        .at(checkInServicePointId));


    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(feeFineActionsClient::getAll, hasSize(1));

    List<JsonObject> createdFeeFineActions = feeFineActionsClient.getAll();
    assertThat("Fee/fine action record should have been created", createdFeeFineActions, hasSize(1));
    FeeFineAction action = FeeFineAction.from(createdFeeFineActions.get(0));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

    UUID loanId = fromString(checkInResponse.getLoan().getString("id"));
    DateTime actionDateTime = action.getDateAction();
    UUID actionId = fromString(action.getId());
    UUID userId = james.getId();

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    assertThat(scheduledNotices,
      hasItems(
        hasScheduledFeeFineNotice(
          actionId, loanId, userId, uponAtTemplateId,
          OVERDUE_FINE_RETURNED, actionDateTime,
          NoticeTiming.UPON_AT, null, true),
        hasScheduledFeeFineNotice(
          actionId, loanId, userId, afterTemplateId,
          OVERDUE_FINE_RETURNED, actionDateTime.plus(afterPeriod.timePeriod()),
          NoticeTiming.AFTER, afterRecurringPeriod, true)
      )
    );
  }

  @Test
  public void overdueFineNoticesAreScheduledOnRenewalWhenConfiguredInPatronNoticePolicy() {
    UUID uponAtTemplateId = randomUUID();
    UUID afterTemplateId = randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

    JsonObject uponAtNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(uponAtTemplateId)
      .withOverdueFineRenewedEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    JsonObject afterNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(afterTemplateId)
      .withOverdueFineRenewedEvent()
      .withAfterTiming(afterPeriod)
      .recurring(afterRecurringPeriod)
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder patronNoticePolicy = new NoticePolicyBuilder()
      .withName("Test policy")
      .withFeeFineNotices(Arrays.asList(
        uponAtNoticeConfiguration,
        afterNoticeConfiguration));

    use(patronNoticePolicy);

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    loansFixture.checkOutByBarcode(nod, james,
      new DateTime(2018, 1, 1, 12, 0, 0, DateTimeZone.UTC));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    feeFinesClient.create(new FeeFineBuilder()
      .withId(randomUUID())
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
    );

    IndividualResource renewedLoan = loansFixture.renewLoan(nod, james);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(feeFineActionsClient::getAll, hasSize(1));

    List<JsonObject> createdFeeFineActions = feeFineActionsClient.getAll();
    assertThat("Fee/fine action record should have been created", createdFeeFineActions, hasSize(1));
    FeeFineAction action = FeeFineAction.from(createdFeeFineActions.get(0));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

    UUID loanId = renewedLoan.getId();
    DateTime actionDateTime = action.getDateAction();
    UUID actionId = fromString(action.getId());
    UUID userId = james.getId();

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    assertThat(scheduledNotices, hasItems(
      hasScheduledFeeFineNotice(
        actionId, loanId, userId, uponAtTemplateId,
        OVERDUE_FINE_RENEWED, actionDateTime,
        NoticeTiming.UPON_AT, null, true),
      hasScheduledFeeFineNotice(
        actionId, loanId, userId, afterTemplateId,
        OVERDUE_FINE_RENEWED, actionDateTime.plus(afterPeriod.timePeriod()),
        NoticeTiming.AFTER, afterRecurringPeriod, true)
    ));
  }

}
