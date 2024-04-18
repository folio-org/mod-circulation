package api.requests.scenarios;

import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.folio.circulation.support.utils.ClockUtil.setDefaultClock;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 */
class MoveRequestPolicyTests extends APITests {
  private NoticePolicyBuilder noticePolicy;

  @BeforeAll
  public static void setUpBeforeClass() {
    FakePubSub.clearPublishedEvents();
  }

  @BeforeEach
  public void setUp() {
    setClock(Clock.fixed(getInstant(), UTC));
  }

  @BeforeEach
  public void setUpNoticePolicy() {
    UUID recallToLoaneeTemplateId = UUID.randomUUID();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoaneeTemplateId)
      .withEventType(NoticeEventType.ITEM_RECALLED.getRepresentation())
      .create();

    noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(singletonList(recallToLoaneeConfiguration));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    setDefaultClock();
  }

  @Test
  void cannotMoveRecallRequestsWithRequestPolicyNotAllowingHolds() {
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String bookMaterialType = materialTypesFixture.book().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.RECALL);
    allowedRequestTypes.add(RequestType.PAGE);
    final String noHoldRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
      "All But Hold", "All but Hold request policy").getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the material type rule m is used instead.
    //The material type rule m allows any patron to place any request but HOLDs on any BOOK, loan or notice types
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy + "\n",
      "m " + bookMaterialType + ": l " + anyLoanPolicy + " r " + noHoldRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(
      2);
    IndividualResource itemToMoveTo = items.get(0);
    IndividualResource itemToMoveFrom = items.get(1);

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    checkOutFixture.checkOutByBarcode(itemToMoveTo, jessica);

    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveTo, charlotte, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    IndividualResource requestByJames = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, james, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    // move james' recall request as a hold shelf request from itemToMoveTo to itemToMoveFrom
    Response response = requestsFixture.attemptMove(new MoveRequestBuilder(
      requestByJames.getId(),
      itemToMoveTo.getId(),
      RequestType.HOLD.getValue()
    ));

    assertThat("Move request should have correct response status code", response.getStatusCode(), is(422));
    assertThat("Move request should have correct response message",
      response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Hold requests are not allowed for this patron and item combination"));

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(1));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(itemToMoveFrom.getId().toString()));

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(itemToMoveTo);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(1));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(itemToMoveFrom);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  void moveRecallRequestWithoutExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out itemToMoveTo
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // notice for the recall is expected
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime());
    assertThat("due date is not the current date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void moveRecallRequestWithExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out itemToMoveTo
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on itemToMoveTo
    requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveTo, charlotte, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime());
    assertThat("due date is not the current date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // There should be 2 notices for each recall
    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(steve));

    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(charlotte));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request unexpectedly sent another patron notice",
      FakeModNotify.getSentPatronNotices(), hasSize(2));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void moveRecallRequestWithoutExistingRecallsAndWithMGDAndRDValuesDoesNotChangeDueDate() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.months(2));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // One notice for the recall is expected
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is not the original date",
      storedLoan.getString("dueDate"), is(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date is not the original due date (3 weeks)",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
      FakeModNotify.getSentPatronNotices(), hasSize(2));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void moveRecallRequestWithoutExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.months(2))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.weeks(3));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // One notice for the recall is expected
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date is not the recall due date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
      FakeModNotify.getSentPatronNotices(), hasSize(2));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void moveRecallRequestWithExistingRecallsAndWithMGDAndRDValuesDoesNotChangeDueDate() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.months(2));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on itemToMoveTo
    requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveTo, charlotte, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is not the original date",
      storedLoan.getString("dueDate"), is(originalDueDate));

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date is not the original due date (3 weeks)",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // There should be 2 notices for each recall
    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(steve));

    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(charlotte));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request unexpectedly sent another patron notice",
      FakeModNotify.getSentPatronNotices(), hasSize(2));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void moveRecallRequestWithExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD() {
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    final IndividualResource itemToMoveTo = items.get(0);
    final IndividualResource itemToMoveFrom = items.get(1);
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.months(2))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(Period.weeks(3));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      itemToMoveTo, steve, getZonedDateTime());

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on itemToMoveTo
    requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveTo, charlotte, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    final String expectedDueDate = formatDateTime(getZonedDateTime().plusWeeks(3));
    assertThat("due date is not the original due date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    // charlotte checks out itemToMoveFrom
    checkOutFixture.checkOutByBarcode(itemToMoveFrom, charlotte);

    // jessica places recall request on itemToMoveFrom
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      itemToMoveFrom, jessica, getZonedDateTime(), RequestType.RECALL.getValue());

    // There should be 2 notices for each recall
    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(steve));

    waitAtMost(1, SECONDS)
      .until(() -> patronNoticesForRecipientWasSent(charlotte));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // move jessica's recall request from itemToMoveFrom to itemToMoveTo
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      itemToMoveTo.getId(),
      RequestType.RECALL.getValue()));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(itemToMoveTo.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request unexpectedly sent another patron notice",
      FakeModNotify.getSentPatronNotices(), hasSize(2));

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  private boolean patronNoticesForRecipientWasSent(IndividualResource steve) {
    return FakeModNotify.getSentPatronNotices()
      .stream()
      .anyMatch(notice -> StringUtils.equals(
        notice.getString("recipientId"),
        steve.getId().toString())
      );
  }

  private void setRules(String rules) {
    try {
      circulationRulesFixture.updateCirculationRules(rules);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
