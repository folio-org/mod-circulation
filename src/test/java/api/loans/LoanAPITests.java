package api.loans;

import static api.requests.RequestsAPICreationTests.setupMissingItem;
import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.InterfaceUrls.loansUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.fixtures.ConfigurationExample;
import api.support.http.InterfaceUrls;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoanAPITests extends APITests {
  @Test
  public void canCreateALoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    loanHasLoanPolicyProperties(loan, loanPoliciesFixture.canCirculateRolling());

    assertThat("ID is taken from item",
      loan.getJsonObject("item").getString("id"), is(itemId));

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

    assertThat("should have change metadata",
      loan.containsKey("metadata"), is(true));

    JsonObject changeMetadata = loan.getJsonObject("metadata");

    assertThat("change metadata should have created date",
      changeMetadata.containsKey("createdDate"), is(true));

    assertThat("change metadata should have updated date",
      changeMetadata.containsKey("updatedDate"), is(true));

    assertThat("due date does not match",
      loan.getString("dueDate"), isEquivalentTo(dueDate));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));

    loanHasExpectedProperties(loan, user);

  }

  @Test
  public void canCreateALoanAtASpecificLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource response = loansClient.createAtSpecificLocation(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    //TODO: Loan Policy should be stored - maybe only works for renewal, possible bug?
//    assertThat("last loan policy should be stored",
//      loan.getString("loanPolicyId"), is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

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

    assertThat("should have change metadata",
      loan.containsKey("metadata"), is(true));

    JsonObject changeMetadata = loan.getJsonObject("metadata");

    assertThat("change metadata should have created date",
      changeMetadata.containsKey("createdDate"), is(true));

    assertThat("change metadata should have updated date",
      changeMetadata.containsKey("updatedDate"), is(true));

    assertThat("due date does not match",
      loan.getString("dueDate"), isEquivalentTo(dueDate));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void cannotCreateALoanForUnknownItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    final UUID unknownItemId = UUID.randomUUID();

    client.post(loansUrl(), new LoanBuilder()
        .open()
        .withId(id)
        .withUserId(userId)
        .withItemId(unknownItemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(String.format("No item with ID %s could be found", unknownItemId)),
      hasUUIDParameter("itemId", unknownItemId))));
  }

  @Test
  public void cannotCreateALoanWithUnknownCheckInServicePointId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    final UUID unknownServicePointId = UUID.randomUUID();

    Response response = loansClient.attemptCreate(new LoanBuilder()
      .withId(id)
      .withCheckinServicePointId(unknownServicePointId)
      .closed()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    assertThat(
      String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check In Service Point does not exist"),
      hasUUIDParameter("checkinServicePointId", unknownServicePointId))));
  }

  @Test
  public void cannotCreateALoanWithUnknownCheckOutServicePointId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    final UUID unknownServicePointId = UUID.randomUUID();

    Response response = loansClient.attemptCreate(new LoanBuilder()
      .withId(id)
      .withCheckoutServicePointId(unknownServicePointId)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    assertThat(
      String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check Out Service Point does not exist"),
      hasUUIDParameter("checkoutServicePointId", unknownServicePointId))));
  }

  @Test
  public void cannotCreateClosedLoanWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(loansUrl(), new LoanBuilder()
        .withItemId(itemId)
        .closed()
        .withNoUserId()
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasNullParameter("userId"))));
  }

  @Test
  public void canCreateClosedLoanInSpecificLocationWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.randomUUID();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loanId)), new LoanBuilder()
        .withId(loanId)
        .withItemId(itemId)
        .withCheckinServicePointId(checkinServicePointId)
        .closed()
        .withNoUserId()
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(204));

    final IndividualResource item = itemsClient.get(itemId);

    assertThat("Item status should be available",
      item.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  @Test
  public void cannotCreateOpenLoanForUnknownRequestingUser()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    final UUID unknownUserId = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(loansUrl(), new LoanBuilder()
        .withId(loanId)
        .withUserId(unknownUserId)
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open()
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasUUIDParameter("userId", unknownUserId))));
  }

  @Test
  public void cannotCreateOpenLoanWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(loansUrl(), new LoanBuilder()
        .withId(loanId)
        .withNoUserId()
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open()
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void cannotCreateOpenLoanAtSpecificLocationWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loanId)), new LoanBuilder()
        .withId(loanId)
        .withNoUserId()
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open()
        .create(),
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void canCreateALoanWithSystemReturnDate()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);
    DateTime systemReturnDate = new DateTime(2017, 4, 1, 12, 0, 0, DateTimeZone.UTC);

    JsonObject builtRequest = new LoanBuilder()
      .closed()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withSystemReturnDate(systemReturnDate)
      .create();

    IndividualResource response = loansClient.create(builtRequest);

    JsonObject loan = response.getJson();

    assertThat("systemReturnDate does not match",
      loan.getString("systemReturnDate"), isEquivalentTo(systemReturnDate));
  }

  @Test
  public void cannotCheckOutAnItemTwice()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.createLoan(smallAngryPlanet, james);

    Response response = loansFixture.attemptToCreateLoan(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasUUIDParameter("itemId", smallAngryPlanet.getId()))));
  }

  @Test
  public void cannotCheckOutMissingItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource missingItem = setupMissingItem(itemsFixture);
    final IndividualResource steve = usersFixture.steve();

    Response response = loansFixture.attemptToCreateLoan(missingItem, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessageContaining("has the item status Missing"),
      hasUUIDParameter("itemId", missingItem.getId()))));
  }

  @Test
  public void cannotCreateLoanThatIsNotOpenOrClosed()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    final Response response = loansClient.attemptCreate(new LoanBuilder()
        .withStatus("Unknown Status")
        .withItemId(smallAngryPlanet.getId())
        .withUserId(jessica.getId()));

    assertThat(
      String.format("Should not be able to create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Loan status must be \"Open\" or \"Closed\"")));
  }

  @Test
  public void canCreateALoanWithoutStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withNoStatus());

    JsonObject loan = response.getJson();

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    loanHasLoanPolicyProperties(response.getJson(), loanPoliciesFixture.canCirculateRolling());

    assertThat("title is taken from item",
      loan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(true));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void creatingAReturnedLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    IndividualResource response = loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC))
      .withReturnDate(new DateTime(2017, 3, 15, 11, 14, 36, DateTimeZone.UTC))
      .withStatus("Closed"));

    JsonObject loan = response.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T10:23:43.000Z"));

    assertThat("status is not closed",
      loan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      loan.getString("action"), is("checkedin"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));

    assertThat("item status snapshot in storage is not available",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Available"));
  }

  @Test
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime dueDate = new DateTime(2016, 11, 15, 8, 26, 53, DateTimeZone.UTC);

    loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .withDueDate(dueDate)
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2016-10-15T08:26:53.000Z"));

    assertThat("due date does not match",
      loan.getString("dueDate"), isEquivalentTo(dueDate));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    loanHasLoanPolicyProperties(loan, loanPoliciesFixture.canCirculateRolling());

    assertThat("ID is taken from item",
      loan.getJsonObject("item").getString("id"), is(itemId));

    assertThat("title is taken from item",
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

    assertThat("has item location",
      loan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    loanHasExpectedProperties(loan, user);

  }

  @Test
  public void canGetLoanPolicyPropertiesForMultipleLoans()
          throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    configClient.create(ConfigurationExample.utcTimezoneConfiguration());
    IndividualResource item1 = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource item2 = itemsFixture.basedUponNod();

    final IndividualResource user1 = usersFixture.jessica();
    final IndividualResource user2 = usersFixture.steve();

    loansFixture.checkOutByBarcode(item1, user1, new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC))
            .getJson();

    loansFixture.checkOutByBarcode(item2, user2, new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC))
            .getJson();

    final IndividualResource loanPolicy = loanPoliciesFixture.canCirculateRolling();

    List<JsonObject> loans = loansClient.getAll();

    loans.forEach(loanJson -> loanHasLoanPolicyProperties(loanJson, loanPolicy));
  }

  @Test
  public void loanFoundByIdDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final InventoryItemResource item = itemsFixture.basedUponNod();

    final UUID loanId = loansFixture.createLoan(item, usersFixture.rebecca()).getId();

    itemsClient.delete(item.getId());

    Response getResponse = loansClient.getById(loanId);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void barcodeIsNotIncludedWhenItemDoesNotHaveOne()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::withNoBarcode)
      .getId();

    UUID userId = usersFixture.charlotte().getId();

    loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("barcode is not taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(false));
  }

  @Test
  public void loanNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canRenewALoanByExtendingTheDueDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = loansFixture.createLoan(item, usersFixture.rebecca());

    JsonObject loanToRenew = loan.copyJson();

    DateTime dueDate = DateTime.parse(loanToRenew.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

    loanToRenew
      .put("action", "renewed")
      .put("dueDate", newDueDate.toString(ISODateTimeFormat.dateTime()))
      .put("renewalCount", 1);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), loanToRenew,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not renewed",
      updatedLoan.getString("action"), is("renewed"));

    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));

    assertThat("due date does not match",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));

    assertThat("renewal count is not 1",
      updatedLoan.getInteger("renewalCount"), is(1));

    assertThat("title is taken from item",
      updatedLoan.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      updatedLoan.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      updatedLoan.containsKey("itemStatus"), is(false));

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(loan.getId()).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void canUpdateAClosedLoanWithoutAUserId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponNod().getId();

    final IndividualResource jessica = usersFixture.jessica();
    UUID checkinServicePointId = servicePointsFixture.cd1().getId();


    IndividualResource loan = loansClient.create(new LoanBuilder()
      .open()
      .withUserId(jessica.getId())
      .withItemId(itemId)
      .withCheckinServicePointId(checkinServicePointId));

    JsonObject updatedLoanRequest = loan.copyJson();

    updatedLoanRequest.getJsonObject("status").put("name", "Closed");
    updatedLoanRequest.remove("userId");

    loansClient.replace(loan.getId(), updatedLoanRequest);

    final JsonObject updatedLoan = loansClient.get(loan.getId()).getJson();

    assertThat("Should be closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    hasNoBorrowerProperties(updatedLoan);
  }
  @Test
  public void multipleClosedLoansHaveNoBorrowerInformation()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException {

    UUID smallAngryPlanetId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID nodId = itemsFixture.basedUponNod().getId();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkinServicePointId2 = servicePointsFixture.cd2().getId();

    final IndividualResource user = usersFixture.jessica();

    final IndividualResource loan1 = loansClient.createAtSpecificLocation(new LoanBuilder()
            .withItemId(smallAngryPlanetId)
            .withCheckinServicePointId(checkinServicePointId)
            .closed()
            .withUserId(user.getId()));

    final IndividualResource loan2 = loansClient.createAtSpecificLocation(new LoanBuilder()
            .withItemId(nodId)
            .closed()
            .withCheckinServicePointId(checkinServicePointId2)
            .withUserId(user.getId()));
    JsonObject updatedLoanRequest = loan1.copyJson();
    updatedLoanRequest.getJsonObject("status").put("name", "Closed");
    updatedLoanRequest.remove("userId");
    loansClient.replace(loan1.getId(), updatedLoanRequest);

    updatedLoanRequest = loan2.copyJson();
    updatedLoanRequest.getJsonObject("status").put("name", "Closed");
    updatedLoanRequest.remove("userId");
    loansClient.replace(loan2.getId(), updatedLoanRequest);

    List<JsonObject> loans = loansClient.getAll();

    loans.forEach(this::hasNoBorrowerProperties);
  }


  @Test
  public void cannotUpdateAnOpenLoanWithoutAUserId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = itemsFixture.basedUponNod().getId();

    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource loan = loansClient.create(new LoanBuilder()
      .open()
      .withUserId(jessica.getId())
      .withItemId(itemId));

    JsonObject updatedLoan = loan.copyJson();

    updatedLoan.remove("userId");

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), updatedLoan,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void updatingACurrentLoanDoesNotChangeItemStatus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource item = itemsFixture.basedUponNod();

    final IndividualResource checkOutResponse = loansFixture.createLoan(item,
      usersFixture.jessica());

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", checkOutResponse.getId())),
      checkOutResponse.getJson().copy(),
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonObject changedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      changedItem.getJsonObject("status").getString("name"), is("Checked out"));

    Response loanFromStorage = loansStorageClient.getById(checkOutResponse.getId());

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void loanInCollectionDoesNotProvideItemInformationForUnknownItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final InventoryItemResource item = itemsFixture.basedUponNod();

    loansFixture.createLoan(item, usersFixture.jessica());

    itemsClient.delete(item.getId());

    CompletableFuture<Response> pageCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
      ResponseHandler.json(pageCompleted));

    Response pageResponse = pageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get page of loans: %s",
      pageResponse.getBody()),
      pageResponse.getStatusCode(), is(200));

    JsonObject firstPage = pageResponse.getJson();

    JsonObject loan = getLoans(firstPage).get(0);

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void canPageLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource user = usersFixture.steve();

    loansFixture.checkOutByBarcode(itemsFixture.basedUponSmallAngryPlanet(), user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponTemeraire(),user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponUprooted(),user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(),user);

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(loansUrl() + "?limit=3",
      ResponseHandler.json(firstPageCompleted));

    client.get(loansUrl() + "?limit=3&offset=3",
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of loans: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get second page of loans: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageLoans = getLoans(firstPage);
    List<JsonObject> secondPageLoans = getLoans(secondPage);

    assertThat(firstPageLoans.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(5));

    assertThat(secondPageLoans.size(), is(2));
    assertThat(secondPage.getInteger("totalRecords"), is(5));

    firstPageLoans.forEach(loan -> loanHasExpectedProperties(loan, user));
    secondPageLoans.forEach(loan -> loanHasExpectedProperties(loan, user));

    assertThat(countOfDistinctTitles(firstPageLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPageLoans), is(greaterThan(1)));
  }

  @Test
  public void canSearchByUserId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    IndividualResource firstUser = usersFixture.steve();
    UUID firstUserId = firstUser.getId();
    IndividualResource secondUser = usersFixture.jessica();
    UUID secondUserId = secondUser.getId();

    String queryTemplate = loansUrl() + "?query=userId=%s";

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(firstUserId));

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponTemeraire())
      .withUserId(firstUserId));

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponUprooted())
      .withUserId(secondUserId));

    loansClient.create(new LoanBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(secondUserId));

    loansClient.create(new LoanBuilder().withItem(
      itemsFixture.basedUponInterestingTimes())
      .withUserId(secondUserId));

    CompletableFuture<Response> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId),
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId),
      ResponseHandler.json(secondUserSeatchCompleted));

    Response firstPageResponse = firstUserSearchCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondUserSeatchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans for first user: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get loans for second user: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageLoans = getLoans(firstPage);
    List<JsonObject> secondPageLoans = getLoans(secondPage);

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(4));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(3));

    firstPageLoans.forEach(loan -> loanHasExpectedProperties(loan, firstUser));
    secondPageLoans.forEach(loan -> loanHasExpectedProperties(loan, secondUser));

    assertThat(countOfDistinctTitles(firstPageLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPageLoans), is(greaterThan(1)));
  }

  @Test
  public void canFindNoResultsFromSearch()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    String queryTemplate = loansUrl() + "?query=userId=%s";

    CompletableFuture<Response> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId),
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId),
      ResponseHandler.json(secondUserSeatchCompleted));

    Response firstPageResponse = firstUserSearchCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondUserSeatchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans for first user: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get loans for second user: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageLoans = getLoans(firstPage);
    List<JsonObject> secondPageLoans = getLoans(secondPage);

    assertThat(firstPageLoans.size(), is(0));
    assertThat(firstPage.getInteger("totalRecords"), is(0));

    assertThat(secondPageLoans.size(), is(0));
    assertThat(secondPage.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canFilterByLoanStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    String queryTemplate = "userId=\"%s\" and status.name=\"%s\"";

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withRandomPastLoanDate());

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponNod())
      .withRandomPastLoanDate());

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withItem(
        itemsFixture.basedUponNod())
      .withStatus("Closed")
      .withRandomPastLoanDate());

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponTemeraire())
      .withRandomPastLoanDate());

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponUprooted())
      .withRandomPastLoanDate());

    loansClient.create(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponInterestingTimes())
      .withRandomPastLoanDate());

    CompletableFuture<Response> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<Response> closedSearchCompleted = new CompletableFuture<>();

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Open"), "UTF-8"),
      ResponseHandler.json(openSearchComppleted));

    client.get(loansUrl(),
      "query=" + URLEncoder.encode(String.format(queryTemplate, userId, "Closed"), "UTF-8"),
      ResponseHandler.json(closedSearchCompleted));

    Response openLoansResponse = openSearchComppleted.get(5, TimeUnit.SECONDS);
    Response closedLoansResponse = closedSearchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get open loans: %s",
      openLoansResponse.getBody()),
      openLoansResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get closed loans: %s",
      closedLoansResponse.getBody()),
      closedLoansResponse.getStatusCode(), is(200));

    JsonObject openLoansPage = openLoansResponse.getJson();
    JsonObject closedLoansPage = closedLoansResponse.getJson();

    List<JsonObject> openLoans = getLoans(openLoansPage);
    List<JsonObject> closedLoans = getLoans(closedLoansPage);

    assertThat(openLoans.size(), is(2));
    assertThat(openLoansPage.getInteger("totalRecords"), is(2));

    assertThat(closedLoans.size(), is(4));
    assertThat(closedLoansPage.getInteger("totalRecords"), is(4));

    openLoans.forEach(loan1 -> loanHasExpectedProperties(loan1, user));

    closedLoans.forEach(loan -> {
        loanHasExpectedProperties(loan, user);
        hasProperty("returnDate", loan, "loan");
      }
    );

    assertThat(countOfDistinctTitles(openLoans), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(closedLoans), is(greaterThan(1)));
  }

  @Test
  public void canGetMultipleLoansWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID smallAngryPlanetId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID nodId = itemsFixture.basedUponNod().getId();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkinServicePointId2 = servicePointsFixture.cd2().getId();

    loansClient.createAtSpecificLocation(new LoanBuilder()
      .withItemId(smallAngryPlanetId)
      .withCheckinServicePointId(checkinServicePointId)
      .closed()
      .withNoUserId());

    loansClient.createAtSpecificLocation(new LoanBuilder()
      .withItemId(nodId)
      .closed()
      .withCheckinServicePointId(checkinServicePointId2)
      .withNoUserId());

    final List<JsonObject> multipleLoans = loansClient.getAll();

    assertThat("Should have two loans",
      multipleLoans.size(), is(2));

    multipleLoans.forEach(this::hasNoBorrowerProperties);
  }

  @Test
  public void canGetMultipleLoansForDifferentBorrowers()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID smallAngryPlanetId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID nodId = itemsFixture.basedUponNod().getId();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkinServicePointId2 = servicePointsFixture.cd2().getId();

    final IndividualResource steveUser = usersFixture.steve();
    final IndividualResource firstLoan = loansClient.createAtSpecificLocation(new LoanBuilder()
      .withItemId(smallAngryPlanetId)
      .withCheckinServicePointId(checkinServicePointId)
      .closed()
      .withUserId(usersFixture.steve().getId()));

    final IndividualResource jessicaUser = usersFixture.jessica();
    final IndividualResource secondLoan = loansClient.createAtSpecificLocation(new LoanBuilder()
      .withItemId(nodId)
      .closed()
      .withCheckinServicePointId(checkinServicePointId2)
      .withUserId(usersFixture.jessica().getId()));

    final List<JsonObject> multipleLoans = loansClient.getAll();

     assertThat("Should have different 'userId' for different loans",
       multipleLoans.get(0).getString("userId"),
       not(multipleLoans.get(1).getString("userId")));

    assertThat("Should have two loans",
      multipleLoans.size(), is(2));
    loanHasExpectedProperties(firstLoan.getJson(), steveUser);
    loanHasExpectedProperties(secondLoan.getJson(), jessicaUser);
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource item = itemsFixture.basedUponNod();

    final UUID loanId = loansFixture.createLoan(item, usersFixture.rebecca()).getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(loansUrl(String.format("/%s", loanId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loansUrl(String.format("/%s", loanId)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCreateALoanWithServicePoints()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkoutServicePointId = servicePointsFixture.cd2().getId();

    loansClient.create(new LoanBuilder()
      .withId(loanId)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    JsonObject loanJson = loansClient.getById(loanId).getJson();

    assertThat("loan has checkin service point id", loanJson.containsKey("checkinServicePointId"), is(true));
    assertThat("loan has checkout service point id", loanJson.containsKey("checkoutServicePointId"), is(true));
    assertThat("loan has checkin service point", loanJson.containsKey("checkinServicePoint"), is(true));
    assertThat("loan has checkout service point", loanJson.containsKey("checkoutServicePoint"), is(true));
  }

  @Test
  public void canCreateMultipleLoansWithServicePoints()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkoutServicePointId = servicePointsFixture.cd2().getId();

    UUID userId = usersFixture.charlotte().getId();

    UUID loan1Id = UUID.randomUUID();
    UUID item1Id = itemsFixture.basedUponDunkirk().getId();

    UUID loan2Id = UUID.randomUUID();
    UUID item2Id = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loan3Id = UUID.randomUUID();
    UUID item3Id = itemsFixture.basedUponUprooted().getId();

    loansClient.create(new LoanBuilder()
      .withId(loan1Id)
      .open()
      .withUserId(userId)
      .withItemId(item1Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    loansClient.create(new LoanBuilder()
      .withId(loan2Id)
      .open()
      .withUserId(userId)
      .withItemId(item2Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    loansClient.create(new LoanBuilder()
      .withId(loan3Id)
      .open()
      .withUserId(userId)
      .withItemId(item3Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.loansUrl(), ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get list of requests: %s",
      getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    List<JsonObject> loanList = getLoans(getResponse.getJson());

    loanList.forEach(loanJson -> {
      loanHasCheckinServicePointProperties(loanJson);
      loanHasCheckoutServicePointProperties(loanJson);
    });

  }

  private void loanHasExpectedProperties(JsonObject loan, IndividualResource user) {
    loanHasExpectedProperties(loan);

    if (user == null) {
      return;
    }

    JsonObject borrower = loan.getJsonObject(LoanProperties.BORROWER);
    JsonObject personalInfo = user.getJson().getJsonObject("personal");

    hasProperty(LoanProperties.BORROWER, loan, "loan");
    hasProperty("firstName", borrower, "borrower", personalInfo.getString("firstName"));
    hasProperty("lastName", borrower, "borrower", personalInfo.getString("lastName"));
    hasProperty("middleName", borrower, "borrower", personalInfo.getString("middleName"));
    hasProperty("barcode", borrower, "borrower", user.getBarcode());

  }

  private void loanHasExpectedProperties(JsonObject loan) {
    hasProperty("id", loan, "loan");
    hasProperty("userId", loan, "loan");
    hasProperty("itemId", loan, "loan");
    hasProperty("loanDate", loan, "loan");
    hasProperty("status", loan, "loan");
    hasProperty("action", loan, "loan");
    hasProperty("item", loan, "loan");

    JsonObject item = loan.getJsonObject("item");

    hasProperty("id", item, "item");
    hasProperty("title", item, "item");
    hasProperty("barcode", item, "item");
    hasProperty("status", item, "item");
    hasProperty("materialType", item, "item");
    hasProperty("callNumber", item, "item");
    hasProperty("contributors", item, "item");

    JsonObject materialType = item.getJsonObject("materialType");

    hasProperty("name", materialType, "material type");

    JsonObject itemStatus = item.getJsonObject("status");

    hasProperty("name", itemStatus, "item status");

    List<JsonObject> contributors = JsonArrayHelper.toList(item.getJsonArray("contributors"));

    assertThat("Should have single contributor",
      contributors.size(), is(1));

    assertThat("Contributor has a name",
      contributors.get(0).containsKey("name"), is(true));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));
  }

  protected void hasProperty(String property, JsonObject resource, String type, Object value) {
    assertThat(String.format("%s should have an %s: %s",
      type, property, resource),
      resource.getMap().get(property), equalTo(value));
  }

  private void hasNoBorrowerProperties(JsonObject loanJson) {
    doesNotHaveProperty("userId", loanJson, "loan");
    doesNotHaveProperty(LoanProperties.BORROWER, loanJson, "loan");
  }

  protected void doesNotHaveProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should NOT have an %s: %s",
            type, property, resource),
            resource.getValue(property), is(nullValue()));
  }

  protected void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have an %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }

  private void loanHasCheckinServicePointProperties(JsonObject loanJson) {
    hasProperty("checkinServicePointId", loanJson, "loan");
    hasProperty("checkinServicePoint", loanJson, "loan");
    hasProperty("name", loanJson.getJsonObject("checkinServicePoint"),
        "checkinServicePoint");
    hasProperty("code", loanJson.getJsonObject("checkinServicePoint"),
        "checkinServicePoint");
  }

  private void loanHasCheckoutServicePointProperties(JsonObject loanJson) {
    hasProperty("checkoutServicePointId", loanJson, "loan");
    hasProperty("checkoutServicePoint", loanJson, "loan");
    hasProperty("name", loanJson.getJsonObject("checkoutServicePoint"),
        "checkoutServicePoint");
    hasProperty("code", loanJson.getJsonObject("checkinServicePoint"),
        "checkoutServicePoint");
  }

  private Integer countOfDistinctTitles(List<JsonObject> loans) {
    return new Long(loans.stream()
      .map(loan -> loan.getJsonObject("item").getString("title"))
      .distinct()
      .count()).intValue();
  }

  private List<JsonObject> getLoans(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("loans"));
  }
}
