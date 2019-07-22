package api.requests.scenarios;

import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import io.vertx.core.json.JsonObject;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 */
public class MoveRequestPolicyTests extends APITests {
  private static final String RECALL_TO_LOANEE = "Recall loanee";
  private static Clock clock;

  private NoticePolicyBuilder noticePolicy;

  @BeforeClass
  public static void setUpBeforeClass() {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  }

  @Before
  public void setUp() {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @Before
  public void setUpNoticePolicy()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    UUID recallToLoaneeTemplateId = UUID.randomUUID();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoaneeTemplateId)
      .withEventType(RECALL_TO_LOANEE)
      .create();

    noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(singletonList(recallToLoaneeConfiguration));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());
  }

  @Test
  public void cannotMoveRecallRequestsWithRequestPolicyNotAllowingHolds()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String bookMaterialType = materialTypesFixture.book().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.RECALL);
    allowedRequestTypes.add(RequestType.PAGE);
    final String noHoldRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
      "All But Hold", "All but Hold request policy").getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the material type rule m is used instead.
    //The material type rule m allows any patron to place any request but HOLDs on any BOOK, loan or notice types
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + "\n",
      "m " + bookMaterialType + ": l " + anyLoanPolicy + " r " + noHoldRequestPolicy +" n " + anyNoticePolicy
    );

    setRules(rules);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    // move james' recall request as a hold shelf request from smallAngryPlanet to interestingTimes
    Response response = requestsFixture.attemptMove(new MoveRequestBuilder(
      requestByJames.getId(),
      smallAngryPlanet.getId(),
      RequestType.HOLD.getValue()
    ));

    assertThat("Move request should have correct response status code", response.getStatusCode(), is(422));
    assertThat("Move request should have correct response message",
      response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Hold requests are not allowed for this patron and item combination"));

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(1));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(1));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void moveRecallRequestWithoutExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out smallAngryPlanet
    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(0));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      smallAngryPlanet.getId(),
      RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the current date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
      patronNoticesClient.getAll().size(), is(1));
  }

  @Test
  public void moveRecallRequestWithExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out smallAngryPlanet
    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on smallAngryPlanet
    requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the current date",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      smallAngryPlanet.getId(),
      RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    List<JsonObject> patronNotices = patronNoticesClient.getAll();

    assertThat("move recall request unexpectedly sent another patron notice",
      patronNotices.size(), is(2));
  }

  @Test
  public void moveRecallRequestWithoutExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
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

    useLoanPolicyAsFallback(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(0));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      smallAngryPlanet.getId(),
      RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (2 months)",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
      patronNoticesClient.getAll().size(), is(1));
  }

  @Test
  public void moveRecallRequestWithExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
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

    useLoanPolicyAsFallback(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on smallAngryPlanet
    requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (2 months)",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(1));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      smallAngryPlanet.getId(),
      RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
      storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request unexpectedly sent another patron notice",
      patronNoticesClient.getAll().size(), is(2));
  }

  private void setRules(String rules) {
    try {
      circulationRulesFixture.updateCirculationRules(rules);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
