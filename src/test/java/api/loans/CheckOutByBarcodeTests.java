package api.loans;

import static api.requests.RequestsAPICreationTests.setupMissingItem;
import static api.support.APITestContext.END_OF_2019_DUE_DATE;
import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasLoanPolicyParameters;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasProxyUserBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasServicePointParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeTests extends APITests {
  @Test
  public void canCheckOutUsingItemAndUserBarcode()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final UUID checkoutServicePointId = UUID.randomUUID();

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasLoanPolicyProperties(loan, loanPoliciesFixture.canCirculateRolling());

    loanHasPatronGroupProperties(loan, "Regular Group");

    assertThat("due date should be 3 weeks after loan date, based upon loan policy",
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusWeeks(3)));

    assertThat("Checkout service point should be stored",
      loan.getString("checkoutServicePointId"), is(checkoutServicePointId));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));

    assertThat("has item information",
      loan.containsKey("item"), is(true));

    assertThat("title is taken from instance",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("call number is 123456", loan.getJsonObject("item")
      .getString("callNumber"), is("123456"));

    assertThat(loan.getJsonObject("item").encode() + " contains 'materialType'",
      loan.getJsonObject("item").containsKey("materialType"), is(true));

    assertThat("materialType is book", loan.getJsonObject("item")
      .getJsonObject("materialType").getString("name"), is("Book"));

    assertThat("item has contributors",
      loan.getJsonObject("item").containsKey("contributors"), is(true));

    JsonArray contributors = loan.getJsonObject("item").getJsonArray("contributors");

    assertThat("item has a single contributor",
      contributors.size(), is(1));

    assertThat("Becky Chambers is a contributor",
      contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    assertThat("item has location",
      loan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    List<JsonObject> patronSessionRecords = patronSessionRecordsClient.getAll();
    assertThat(patronSessionRecords, hasSize(1));

    JsonObject sessionRecord = patronSessionRecords.get(0);
    assertThat(sessionRecord.getString("patronId"), is(steve.getId()));
    assertThat(sessionRecord.getString("loanId"), is(response.getId()));
    assertThat(sessionRecord.getString("actionType"), is("Check-out"));
  }

  @Test
  public void canCheckOutUsingFixedDueDateLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    useExampleFixedPolicyCirculationRules();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2019, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasPatronGroupProperties(loan, "Regular Group");

    loanHasLoanPolicyProperties(loan, loanPoliciesFixture.canCirculateFixed());

    assertThat("due date should be based upon fixed due date schedule",
      loan.getString("dueDate"),
      isEquivalentTo(END_OF_2019_DUE_DATE));
  }

  @Test
  public void canCheckOutUsingDueDateLimitedRollingLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.days(30))
      .limitedBySchedule(dueDateLimitScheduleId);

    final IndividualResource loanPolicyResource = loanPoliciesFixture.create(dueDateLimitedPolicy);
    UUID dueDateLimitedPolicyId = loanPolicyResource.getId();

    useFallbackPolicies(
      dueDateLimitedPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId()
    );

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasPatronGroupProperties(loan, "Regular Group");

    loanHasLoanPolicyProperties(loan, loanPolicyResource);

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canGetLoanCreatedWhilstCheckingOut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    assertThat("Location header should be present",
      response.getLocation(), is(notNullValue()));

    final CompletableFuture<Response> completed = new CompletableFuture<>();

    client.get(APITestContext.circulationModuleUrl(response.getLocation()),
      ResponseHandler.json(completed));

    final Response getResponse = completed.get(2, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
  }

  @Test
  public void canCheckOutWithoutLoanDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime requestDate = DateTime.now();

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"),
      withinSecondsAfter(Seconds.seconds(10), requestDate));
  }

  @Test
  public void cannotCheckOutWhenLoaneeCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //TODO: Review this to see if can simplify by not creating user at all?
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    usersFixture.remove(steve);

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Could not find user with matching barcode"),
      hasUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutWhenLoaneeIsInactive()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve(UserBuilder::inactive);

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out to inactive user"),
      hasUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutByProxyWhenProxyingUserIsInactive()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource james = usersFixture.james();
    final IndividualResource steve = usersFixture.steve(UserBuilder::inactive);

    proxyRelationshipsFixture.currentProxyFor(james, steve);

    final Response response = loansFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(james)
        .proxiedBy(steve)
        .at(UUID.randomUUID()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out via inactive proxying user"),
      hasProxyUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutByProxyWhenNoRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final Response response = loansFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(james)
        .at(UUID.randomUUID()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out item via proxy when relationship is invalid"),
      hasProxyUserBarcodeParameter(james))));
  }

  @Test
  public void cannotCheckOutWhenItemCannotBeFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    itemsClient.delete(smallAngryPlanet.getId());

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No item with barcode 036000291452 could be found"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotCheckOutWhenItemIsAlreadyCheckedOut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotCheckOutWhenItemIsMissing() throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource missingItem = setupMissingItem(itemsFixture);
    final IndividualResource steve = usersFixture.steve();
    final Response response = loansFixture.attemptCheckOutByBarcode(missingItem, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessageContaining("has the item status Missing"),
      hasItemBarcodeParameter(missingItem))));
  }

  @Test
  public void cannotCheckOutWhenOpenLoanAlreadyExists()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    loansStorageClient.create(new LoanBuilder()
      .open()
      .withItemId(smallAngryPlanet.getId())
      .withUserId(jessica.getId()));

    final Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out item that already has an open loan"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void canCheckOutViaProxy()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    proxyRelationshipsFixture.currentProxyFor(jessica, james);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(james)
        .at(UUID.randomUUID()));

    JsonObject loan = response.getJson();

    assertThat("user id does not match",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(james.getId()));
  }

  @Test
  public void cannotCheckOutWhenLoanPolicyDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID nonExistentloanPolicyId = UUID.randomUUID();

    useFallbackPolicies(
      nonExistentloanPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId()
    );

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final Response response = loansFixture.attemptCheckOutByBarcode(500,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check circulation rules",
      nonExistentloanPolicyId)));
  }

  @Test
  public void cannotCheckOutWhenServicePointOfCheckoutNotPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final Response response = loansFixture.attemptCheckOutByBarcode(422,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(james)
        .on(loanDate));

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check out must be performed at a service point"),
      hasServicePointParameter(null))));
  }

  @Test
  public void canCheckOutUsingItemBarcodeThatContainsSpaces()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource steve = usersFixture.steve();
    IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet(item -> item.withBarcode("12345 67890"));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutUsingUserBarcodeThatContainsSpaces()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource steve
      = usersFixture.steve(user -> user.withBarcode("12345 67890"));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutUsingProxyUserBarcodeThatContainsSpaces()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource steve
      = usersFixture.steve(user -> user.withBarcode("12345 67890"));

    proxyRelationshipsFixture.currentProxyFor(jessica, steve);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("proxy user ID should match barcode",
      loan.getString("proxyUserId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutOnOrderItem()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(item -> item.onOrder());

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutOnOrderItemWithRequest()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(item -> item.onOrder());

    final IndividualResource jessica = usersFixture.jessica();

    requestsFixture.place(
      new RequestBuilder()
        .withItemId(smallAngryPlanet.getId())
        .withRequesterId(jessica.getId())
        .withPickupServicePoint(servicePointsFixture.cd1())
    );

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutInProcessItem()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(item -> item.inProcess());

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutInProcessItemWithRequest()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(item -> item.inProcess());

    final IndividualResource jessica = usersFixture.jessica();

    requestsFixture.place(
      new RequestBuilder()
        .withItemId(smallAngryPlanet.getId())
        .withRequesterId(jessica.getId())
        .withPickupServicePoint(servicePointsFixture.cd1())
    );

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void checkOutFailsWhenCirculationRulesReferenceInvalidLoanPolicyId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    setInvalidLoanPolicyReferenceInRules("some-loan-policy");

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    final Response response = loansFixture.attemptCheckOutByBarcode(500,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    assertThat(response.getBody(),
      is("Loan policy some-loan-policy could not be found, please check circulation rules"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AVAILABLE));
  }

  @Test
  public void checkOutDoesNotFailWhenCirculationRulesReferenceInvalidNoticePolicyId()
  throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    setInvalidNoticePolicyReferenceInRules("some-notice-policy");

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void cannotCheckOutWhenItemIsNotLoanable()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource notLoanablePolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Not Loanable Policy")
        .withLoanable(false));

    useFallbackPolicies(
      notLoanablePolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());

    InventoryItemResource nod = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve();
    Response response = loansFixture.attemptCheckOutByBarcode(nod, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is not loanable"),
      hasItemBarcodeParameter(nod),
      hasLoanPolicyParameters(notLoanablePolicy))));
  }
}
