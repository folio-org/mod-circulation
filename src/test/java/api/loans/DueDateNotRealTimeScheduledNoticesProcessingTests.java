package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals;
import static api.support.fixtures.TemplateContextMatchers.getMultipleLoansContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.ConfigurationExample;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class DueDateNotRealTimeScheduledNoticesProcessingTests extends APITests {

  @Test
  public void uponAtDueDateNoticesShouldBeSentInGroups()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();
    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james, loanDate);
    IndividualResource interestingTimesToJamesLoan = loansFixture.checkOutByBarcode(interestingTimes, james, loanDate);


    IndividualResource rebecca = usersFixture.rebecca();
    InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    InventoryItemResource dunkirk = itemsFixture.basedUponDunkirk();
    IndividualResource temeraireToRebeccaLoan = loansFixture.checkOutByBarcode(temeraire, rebecca, loanDate);
    IndividualResource dunkirkToRebeccaLoan = loansFixture.checkOutByBarcode(dunkirk, rebecca, loanDate);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(4));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));

    DateTime afterLoanDueDateTime = dueDate.plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(2));

    Matcher<? super String> loanPolicyMatcher = toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals());

    Matcher<? super String> jamesUserContextMatcher = toStringMatcher(getUserContextMatchers(james));

    Matcher<? super String> nodMatcher =
      toStringMatcher(getItemContextMatchers(nod, true));
    Matcher<? super String> interestingTimesMatcher =
      toStringMatcher(getItemContextMatchers(interestingTimes, true));

    Matcher<? super String> nodToJamesLoanMatcher =
      toStringMatcher(getLoanContextMatchers(nodToJamesLoan));
    Matcher<? super String> interestingTimesToJamesLoanMatcher =
      toStringMatcher(getLoanContextMatchers(interestingTimesToJamesLoan));

    Matcher<? super String> noticeToJamesContextMatcher =
      allOf(
        jamesUserContextMatcher,
        hasJsonPath("loans[*]", hasItems(
          allOf(nodMatcher, nodToJamesLoanMatcher, loanPolicyMatcher),
          allOf(interestingTimesMatcher, interestingTimesToJamesLoanMatcher, loanPolicyMatcher))));

    Matcher<? super String> rebeccaUserContextMatcher = toStringMatcher(getUserContextMatchers(rebecca));

    Matcher<? super String> temeraireMatcher =
      toStringMatcher(getItemContextMatchers(temeraire, true));
    Matcher<? super String> dunkirkMatcher =
      toStringMatcher(getItemContextMatchers(dunkirk, true));

    Matcher<? super String> temeraireToRebeccaLoanMatcher =
      toStringMatcher(getLoanContextMatchers(temeraireToRebeccaLoan));
    Matcher<? super String> dunkirkToRebeccaLoanMatcher =
      toStringMatcher(getLoanContextMatchers(dunkirkToRebeccaLoan));

    Matcher<? super String> noticeToRebeccaContextMatcher =
      allOf(
        rebeccaUserContextMatcher,
        hasJsonPath("loans[*]", hasItems(
          allOf(temeraireMatcher, temeraireToRebeccaLoanMatcher, loanPolicyMatcher),
          allOf(dunkirkMatcher, dunkirkToRebeccaLoanMatcher, loanPolicyMatcher))));

    MatcherAssert.assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(james.getId(), templateId, noticeToJamesContextMatcher),
      hasEmailNoticeProperties(rebecca.getId(), templateId, noticeToRebeccaContextMatcher)));
  }

  @Test
  public void beforeRecurringNoticesAreRescheduled()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();
    Period beforePeriod = Period.weeks(1);
    Period recurringPeriod = Period.days(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .recurring(recurringPeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james, loanDate);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(), james, loanDate);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));

    DateTime timeForNoticeToBeSent = dueDate.minusWeeks(1);
    DateTime nextDayAfterBeforeNoticeShouldBeSend = timeForNoticeToBeSent.withTime(LocalTime.MIDNIGHT).plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(nextDayAfterBeforeNoticeShouldBeSend);

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();

    DateTime newNextRunTime = timeForNoticeToBeSent.plus(recurringPeriod.timePeriod());

    assertTrue("all scheduled notices are rescheduled", scheduledNotices.stream()
      .map(entries -> entries.getString("nextRunTime"))
      .map(DateTime::parse)
      .allMatch(newNextRunTime::isEqual));

    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void beforeNoticesAreNotSentIfLoanIsClosed()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();
    Period beforePeriod = Period.weeks(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james, loanDate);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));

    loansFixture.checkInByBarcode(nod);

    DateTime timeForNoticeToBeSent = dueDate.minusWeeks(1);
    DateTime nextDayAfterBeforeNoticeShouldBeSend = timeForNoticeToBeSent.withTime(LocalTime.MIDNIGHT).plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(nextDayAfterBeforeNoticeShouldBeSend);

    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void processingTakesNoticesLimitedByConfiguration()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();
    Period beforePeriod = Period.weeks(1);

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withBeforeTiming(beforePeriod)
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    //Generate several loans
    for (int i = 0; i < 4; i++) {
      String baseBarcode = Integer.toString(i);
      loansFixture.checkOutByBarcode(
        itemsFixture.basedUponNod(b -> b.withBarcode(baseBarcode + "1")), james);
      loansFixture.checkOutByBarcode(
        itemsFixture.basedUponNod((b -> b.withBarcode(baseBarcode + "2"))), steve);
      loansFixture.checkOutByBarcode(
        itemsFixture.basedUponNod((b -> b.withBarcode(baseBarcode + "3"))), rebecca);
    }

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(12));

    int noticesLimitConfig = 10;
    configClient.create(ConfigurationExample.schedulerNoticesLimitConfiguration(Integer.toString(noticesLimitConfig)));

    //Should fetch 10 notices, when total records is 12
    //So that notices for one of the users should not be processed
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(loanDate.plusYears(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices, hasSize(4));

    long numberOfUniqueUserIds = scheduledNotices.stream()
      .map(notice -> notice.getString("recipientUserId"))
      .distinct().count();
    assertThat(numberOfUniqueUserIds, is(1L));
  }

  @Test
  public void noticeIsDeletedIfReferencedLoanDoesNotExist()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james, loanDate);

    loansStorageClient.delete(nodToJamesLoan);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void noticeIsDeletedIfReferencedItemDoesNotExist()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james, loanDate);

    itemsClient.delete(nod);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void noticeIsDeletedIfReferencedUserDoesNotExist()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james, loanDate);

    usersClient.delete(james);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    DateTime dueDate = new DateTime(nodToJamesLoan.getJson().getString("dueDate"));
    DateTime afterLoanDueDateTime = dueDate.plusDays(1);
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(afterLoanDueDateTime);

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void missingReferencedEntitiesDoNotBlockProcessing()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    UUID templateId = UUID.randomUUID();

    JsonObject uponAtDueDateNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withUponAtTiming()
      .sendInRealTime(false)
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with due date notices")
      .withLoanNotices(Collections.singletonList(uponAtDueDateNoticeConfig));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2019, 8, 23, 10, 30);

    // users
    IndividualResource james = usersFixture.james();
    IndividualResource steve = usersFixture.steve();
    IndividualResource jessica = usersFixture.jessica();

    // items
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    InventoryItemResource planet = itemsFixture.basedUponSmallAngryPlanet();
    InventoryItemResource times = itemsFixture.basedUponInterestingTimes();
    InventoryItemResource uprooted = itemsFixture.basedUponUprooted();
    InventoryItemResource dunkirk = itemsFixture.basedUponDunkirk();

    // loans
    IndividualResource nodToJames = loansFixture.checkOutByBarcode(nod, james, loanDate.plusHours(1));
    IndividualResource temeraireToJames = loansFixture.checkOutByBarcode(temeraire, james, loanDate.plusHours(2));
    IndividualResource planetToJames = loansFixture.checkOutByBarcode(planet, james, loanDate.plusHours(3));
    IndividualResource timesToSteve = loansFixture.checkOutByBarcode(times, steve, loanDate.plusHours(4));
    IndividualResource uprootedToSteve = loansFixture.checkOutByBarcode(uprooted, steve, loanDate.plusHours(5));
    IndividualResource dunkirkToJessica = loansFixture.checkOutByBarcode(dunkirk, jessica, loanDate.plusHours(6));

    loansClient.delete(temeraireToJames);
    itemsClient.delete(times);
    usersClient.delete(jessica);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(6));

    DateTime dueDate = new DateTime(nodToJames.getJson().getString("dueDate"));
    scheduledNoticeProcessingClient.runDueDateNotRealTimeNoticesProcessing(dueDate.plusDays(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
    assertThat(sentNotices, hasSize(2));

    Matcher<? super String> loanPolicyMatcher = toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals());

    Matcher<? super String> noticeToJamesContextMatcher =
      getMultipleLoansContextMatcher(
        james,
        Arrays.asList(
          Pair.of(nodToJames, nod),
          Pair.of(planetToJames, planet)),
        loanPolicyMatcher);

    Matcher<? super String> noticeToSteveContextMatcher =
      getMultipleLoansContextMatcher(
        steve,
        Collections.singletonList(
          Pair.of(uprootedToSteve, uprooted)),
        loanPolicyMatcher);

    MatcherAssert.assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(james.getId(), templateId, noticeToJamesContextMatcher),
      hasEmailNoticeProperties(steve.getId(), templateId, noticeToSteveContextMatcher)));
  }

}
