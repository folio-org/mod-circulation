package api.loans;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverrideCheckOutByBarcodeRequestBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OverrideCheckOutByBarcodeTests extends APITests {

  private static final DateTime TEST_LOAN_DATE =
    new DateTime(2019, 4, 10, 11, 35, 48, DateTimeZone.UTC);
  private static final DateTime TEST_DUE_DATE =
    new DateTime(2019, 4, 20, 11, 30, 0, DateTimeZone.UTC);
  private static final String TEST_COMMENT = "Some comment";
  private static final String CHECKED_OUT_THROUGH_OVERRIDE = "checkedOutThroughOverride";

  @Test
  public void canOverrideCheckoutWhenItemIsNotLoanable()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    setNotLoanablePolicy();
    IndividualResource response = loansFixture.overrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withComment(TEST_COMMENT)
    );

    final JsonObject loan = response.getJson();

    assertThat("action should be checkedOutThroughOverride",
      loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));

    assertThat("due date should equal to due date from request",
      loan.getString("dueDate"), isEquivalentTo(TEST_DUE_DATE));

    assertThat("due date should equal to due date from request",
      loan.getString("actionComment"), is(TEST_COMMENT));

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be checkedout",
      loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(TEST_LOAN_DATE));

    assertThat("due date should be 3 weeks after loan date, based upon loan policy",
      loan.getString("dueDate"), isEquivalentTo(TEST_DUE_DATE));

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
  }

  @Test
  public void cannotOverrideCheckoutWhenItemIsLoanable()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    Response response = loansFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
        .withComment(TEST_COMMENT)
    );

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override is not allowed when item is loanable"))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsNotPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    setNotLoanablePolicy();
    Response response = loansFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withComment(TEST_COMMENT)
    );

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override should be performed with due date specified"),
      hasParameter("dueDate", null))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsBeforeLoanDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    setNotLoanablePolicy();
    DateTime invalidDueDate = TEST_LOAN_DATE.minusDays(2);
    Response response = loansFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(invalidDueDate)
        .withComment(TEST_COMMENT)
    );

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Due date should be later than loan date"),
      hasParameter("dueDate", invalidDueDate.toString()))));
  }

  @Test
  public void cannotOverrideCheckoutWhenDueDateIsTheSameAsLoanDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    setNotLoanablePolicy();
    Response response = loansFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_LOAN_DATE)
        .withComment(TEST_COMMENT)
    );

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Due date should be later than loan date"),
      hasParameter("dueDate", TEST_LOAN_DATE.toString()))));
  }

  @Test
  public void cannotOverrideCheckoutWhenCommentIsNotPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    setNotLoanablePolicy();
    Response response = loansFixture.attemptOverrideCheckOutByBarcode(
      new OverrideCheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId)
        .on(TEST_LOAN_DATE)
        .withDueDate(TEST_DUE_DATE)
    );

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override should be performed with the comment specified"),
      hasParameter("comment", null))));
  }

  private void setNotLoanablePolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    LoanPolicyBuilder notLoanablePolicy = new LoanPolicyBuilder()
      .withName("Not Loanable Policy")
      .withLoanable(false);
    useLoanPolicyAsFallback(
      loanPoliciesFixture.create(notLoanablePolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId());
  }

}
