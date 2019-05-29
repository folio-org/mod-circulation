package api.loans;

import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNoticeProperties;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeScheduledNoticesTests extends APITests {

  private static final String BEFORE_TIMING = "Before";
  private static final String UPON_AT_TIMING = "Upon At";
  private static final String AFTER_TIMING = "After";

  @Test
  public void allDueDateNoticesShouldBeScheduledOnCheckoutWhenPolicyDefinesDueDateNoticeConfiguration()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID beforeTemplateId = UUID.randomUUID();
    Period beforePeriod = Period.days(2);
    Period beforeRecurringPeriod = Period.hours(6);

    UUID uponAtTemplateId = UUID.randomUUID();

    UUID afterTemplateId = UUID.randomUUID();
    Period afterPeriod = Period.days(3);
    Period afterRecurringPeriod = Period.hours(4);

    JsonObject beforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(beforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .recurring(beforeRecurringPeriod)
      .sendInRealTime(true)
      .create();
    JsonObject uponAtDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(uponAtTemplateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();
    JsonObject afterDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(afterTemplateId)
      .withDueDateEvent()
      .withAfterTiming(afterPeriod)
      .recurring(afterRecurringPeriod)
      .sendInRealTime(true)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Arrays.asList(
        beforeDueDateNoticeConfiguration,
        uponAtDueDateNoticeConfiguration,
        afterDueDateNoticeConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));
    DateTime dueDate = new DateTime(loan.getJson().getString("dueDate"));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticeClient::getAll, Matchers.hasSize(3));

    List<JsonObject> scheduledNotices = scheduledNoticeClient.getAll();
    MatcherAssert.assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNoticeProperties(
          loan.getId(), dueDate.minus(beforePeriod.toTimePeriod()),
          BEFORE_TIMING, beforeTemplateId,
          beforeRecurringPeriod, true),
        hasScheduledLoanNoticeProperties(
          loan.getId(), dueDate,
          UPON_AT_TIMING, uponAtTemplateId,
          null, false),
        hasScheduledLoanNoticeProperties(
          loan.getId(), dueDate.plus(afterPeriod.toTimePeriod()),
          AFTER_TIMING, afterTemplateId,
          afterRecurringPeriod, true)
      ));
  }

  @Test
  public void severalBeforeDueDateNoticesShouldBeScheduledOnCheckout()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID firstBeforeTemplateId = UUID.randomUUID();
    Period firstBeforePeriod = Period.weeks(1);
    UUID secondBeforeTemplateId = UUID.randomUUID();
    Period secondBeforePeriod = Period.hours(12);

    JsonObject firstBeforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(firstBeforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(firstBeforePeriod)
      .sendInRealTime(false)
      .create();
    JsonObject secondBeforeDueDateNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(secondBeforeTemplateId)
      .withDueDateEvent()
      .withBeforeTiming(secondBeforePeriod)
      .sendInRealTime(true)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with before due date notices")
      .withLoanNotices(Arrays.asList(
        firstBeforeDueDateNoticeConfiguration,
        secondBeforeDueDateNoticeConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));
    DateTime dueDate = new DateTime(loan.getJson().getString("dueDate"));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticeClient::getAll, Matchers.hasSize(2));

    List<JsonObject> scheduledNotices = scheduledNoticeClient.getAll();
    MatcherAssert.assertThat(scheduledNotices,
      hasItems(
        hasScheduledLoanNoticeProperties(
          loan.getId(), dueDate.minus(firstBeforePeriod.toTimePeriod()),
          BEFORE_TIMING, firstBeforeTemplateId,
          null, false),
        hasScheduledLoanNoticeProperties(
          loan.getId(), dueDate.minus(secondBeforePeriod.toTimePeriod()),
          BEFORE_TIMING, secondBeforeTemplateId,
          null, true)
      ));
  }


  @Test
  public void noNoticesShouldBeScheduledWhenPolicyDoesNotDefineTimeBasedNotics()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckOutEvent()
      .create();
    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy without time-based notices")
      .withLoanNotices(Arrays.asList(
        checkOutNoticeConfiguration,
        checkInNoticeConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate =
      new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    TimeUnit.SECONDS.sleep(1);
    List<JsonObject> scheduledNotices = scheduledNoticeClient.getAll();
    assertThat("No notices should be scheduled",
      scheduledNotices, Matchers.empty());
  }


}
