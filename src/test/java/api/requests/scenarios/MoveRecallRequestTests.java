package api.requests.scenarios;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 *
 * @see <a href="https://issues.folio.org/browse/CIRC-316">CIRC-316</a>
 */
@RunWith(JUnitParamsRunner.class)
public class MoveRecallRequestTests extends APITests {
  private static final String RECALL_TO_LOANEE = "Recall loanee";
  private static Clock clock;
  
  private NoticePolicyBuilder noticePolicy;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  }

  @Before
  public void setUp() throws Exception {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @Before
  public void setUpNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    UUID recallToLoaneeTemplateId = UUID.randomUUID();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoaneeTemplateId)
      .withEventType(RECALL_TO_LOANEE)
      .create();

    noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(Arrays.asList(
        recallToLoaneeConfiguration));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());
  }

  @Test
  public void moveRecallRequestWithoutExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out smallAngryPlanet
    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out uponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);
    
    // jessica places recall request on uponInterestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        uponInterestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from uponInterestingTimes to smallAngryPlanet
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

    List<JsonObject> patronNotices = patronNoticesClient.getAll();

    assertThat("move recall request notice has not been sent",
        patronNotices.size(), is(2));
  }
  
  @Test
  public void moveRecallRequestWithExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
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


    // charlotte checks out uponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    // jessica places recall request on uponInterestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        uponInterestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from uponInterestingTimes to smallAngryPlanet
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
    final IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
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

    // charlotte checks out uponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);
    
    // jessica places recall request on uponInterestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        uponInterestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from uponInterestingTimes to smallAngryPlanet
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

    List<JsonObject> patronNotices = patronNoticesClient.getAll();

    assertThat("move recall request notice has not been sent",
        patronNotices.size(), is(2));
  }
  
  @Test
  public void moveRecallRequestWithExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
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


    // charlotte checks out uponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);
    
    // jessica places recall request on uponInterestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        uponInterestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from uponInterestingTimes to smallAngryPlanet
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
}
