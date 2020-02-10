package api.loans;

import static api.requests.RequestsAPICreationTests.setupMissingItem;
import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.Limit.limit;
import static api.support.http.Offset.offset;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.LoanProperties.BORROWER;
import static org.folio.circulation.support.JsonArrayHelper.toList;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.AccountBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.fixtures.ConfigurationExample;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoanAPITests extends APITests {
  @Test
  public void canCreateALoan() {
    UUID id = UUID.randomUUID();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    UUID itemId = smallAngryPlanet.getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
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

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

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

    assertThat("has item enumeration",
      item.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      item.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      item.getString("volume"), is("testVolume"));

    loanHasExpectedProperties(loan, user);
  }

  @Test
  public void canGetLoanWithoutOpenFeesFines() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(response)
      .feeFineStatusClosed()
      .withRemainingFeeFine(150)
    );

    JsonObject loan = loansFixture.getLoanById(id).getJson();

    loanHasFeeFinesProperties(loan, 0);
  }

  @Test
  public void canGetMultipleFeesFinesForSingleLoan() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate));

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(response)
      .withRemainingFeeFine(150)
    );

    JsonObject loan = loansFixture.getLoanById(id).getJson();

    loanHasFeeFinesProperties(loan, 150d);

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(response)
      .withRemainingFeeFine(150)
    );

    loan = loansFixture.getLoanById(id).getJson();

    loanHasFeeFinesProperties(loan, 300d);
  }

  @Test
  public void canGetMultipleFeesFinesForMultipleLoans() {
    configClient.create(ConfigurationExample.utcTimezoneConfiguration());
    IndividualResource item1 = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource item2 = itemsFixture.basedUponNod();

    final IndividualResource user1 = usersFixture.jessica();
    final IndividualResource user2 = usersFixture.steve();

    IndividualResource loan1 = loansFixture.checkOutByBarcode(
      item1, user1, new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    IndividualResource loan2 = loansFixture.checkOutByBarcode(
      item2, user2, new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(loan1)
      .withRemainingFeeFine(100)
    );

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(loan1)
      .withRemainingFeeFine(100)
    );

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(loan2)
      .withRemainingFeeFine(99)
    );

    accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(loan2)
      .withRemainingFeeFine(1000)
    );

    MultipleJsonRecords loans = loansFixture.getAllLoans();

    JsonObject fetchedLoan1 = loans.getById(loan1.getId());
    JsonObject fetchedLoan2 = loans.getById(loan2.getId());

    loanHasFeeFinesProperties(fetchedLoan1, 200);
    loanHasFeeFinesProperties(fetchedLoan2, 1099);
  }

  @Test
  public void canCreateALoanAtASpecificLocation() {
    UUID id = UUID.randomUUID();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      builder -> builder
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    UUID itemId = smallAngryPlanet.getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

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

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

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

    assertThat("has item enumeration",
      item.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      item.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      item.getString("volume"), is("testVolume"));
  }

  @Test
  public void cannotCreateALoanForUnknownItem() {
    UUID id = UUID.randomUUID();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    final UUID unknownItemId = UUID.randomUUID();

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
        .open()
        .withId(id)
        .withUserId(userId)
        .withItemId(unknownItemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate),
      UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(format("No item with ID %s could be found", unknownItemId)),
      hasUUIDParameter("itemId", unknownItemId))));
  }

  @Test
  public void cannotCreateALoanWithUnknownCheckInServicePointId() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    final UUID unknownServicePointId = UUID.randomUUID();

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
      .withId(id)
      .withCheckinServicePointId(unknownServicePointId)
      .closed()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate), UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check In Service Point does not exist"),
      hasUUIDParameter("checkinServicePointId", unknownServicePointId))));
  }

  @Test
  public void cannotCreateALoanWithUnknownCheckOutServicePointId() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    final UUID unknownServicePointId = UUID.randomUUID();

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
      .withId(id)
      .withCheckoutServicePointId(unknownServicePointId)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate), UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check Out Service Point does not exist"),
      hasUUIDParameter("checkoutServicePointId", unknownServicePointId))));
  }

  @Test
  public void cannotCreateClosedLoanWithoutUserId() {
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
        .withItemId(itemId)
        .closed()
        .withNoUserId(),
      UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasNullParameter("userId"))));
  }

  @Test
  public void canCreateClosedLoanInSpecificLocationWithoutUserId() {
    UUID loanId = UUID.randomUUID();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.createLoanAtSpecificLocation(loanId, new LoanBuilder()
        .withId(loanId)
        .withItemId(itemId)
        .withCheckinServicePointId(checkinServicePointId)
        .closed()
        .withNoUserId());

    final IndividualResource item = itemsClient.get(itemId);

    assertThat("Item status should be available",
      item.getJson().getJsonObject("status").getString("name"), is("Available"));
  }

  @Test
  public void cannotCreateOpenLoanForUnknownRequestingUser() {
    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    final UUID unknownUserId = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
        .withId(loanId)
        .withUserId(unknownUserId)
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open(),
      UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasUUIDParameter("userId", unknownUserId))));
  }

  @Test
  public void cannotCreateOpenLoanWithoutUserId() {
    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
        .withId(loanId)
        .withNoUserId()
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open(),
      UNPROCESSABLE_ENTITY);

    assertThat(format("Should not create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void cannotCreateOpenLoanAtSpecificLocationWithoutUserId() {
    UUID loanId = UUID.randomUUID();

    final UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    Response response = loansFixture.attemptToCreateLoanAtSpecificLocation(
      loanId, new LoanBuilder()
        .withId(loanId)
        .withNoUserId()
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .open());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void canCreateALoanWithSystemReturnDate() {
    UUID id = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);
    DateTime systemReturnDate = new DateTime(2017, 4, 1, 12, 0, 0, UTC);

    IndividualResource response = loansFixture.createLoan(
      new LoanBuilder()
        .closed()
        .withId(id)
        .withUserId(userId)
        .withItemId(itemId)
        .withLoanDate(loanDate)
        .withDueDate(dueDate)
        .withSystemReturnDate(systemReturnDate));

    JsonObject loan = response.getJson();

    assertThat("systemReturnDate does not match",
      loan.getString("systemReturnDate"), isEquivalentTo(systemReturnDate));
  }

  @Test
  public void cannotCheckOutAnItemTwice() {
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
  public void cannotCheckOutMissingItem() {
    final IndividualResource missingItem = setupMissingItem(itemsFixture);
    final IndividualResource steve = usersFixture.steve();

    Response response = loansFixture.attemptToCreateLoan(missingItem, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessageContaining("has the item status Missing"),
      hasUUIDParameter("itemId", missingItem.getId()))));
  }

  @Test
  public void cannotCheckOutDeclaredLostItem() {
    final IndividualResource declaredLostItem = itemsFixture.setupDeclaredLostItem();
    final IndividualResource steve = usersFixture.steve();

    Response response = loansFixture.attemptToCreateLoan(declaredLostItem, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessageContaining("has the item status Declared lost"),
      hasUUIDParameter("itemId", declaredLostItem.getId()))));
  }

  @Test
  public void cannotCreateLoanThatIsNotOpenOrClosed() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    final Response response = loansFixture.attemptToCreateLoan(new LoanBuilder()
        .withStatus("Unknown Status")
        .withItemId(smallAngryPlanet.getId())
        .withUserId(jessica.getId()),
      UNPROCESSABLE_ENTITY);

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Loan status must be \"Open\" or \"Closed\"")));
  }

  @Test
  public void canCreateALoanWithoutStatus() {
    UUID id = UUID.randomUUID();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      builder -> builder
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    UUID itemId = smallAngryPlanet.getId();

    UUID userId = usersFixture.charlotte().getId();

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withNoStatus());

    JsonObject loan = response.getJson();

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checkedout",
      loan.getString("action"), is("checkedout"));

    loanHasLoanPolicyProperties(response.getJson(),
      loanPoliciesFixture.canCirculateRolling());

    assertThat("title is taken from item",
      loan.getJsonObject("item").containsKey("title"), is(true));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(true));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not checked out",
      item.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("has item enumeration",
      item.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      item.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      item.getString("volume"), is("testVolume"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void creatingAReturnedLoanDoesNotChangeItemStatus() {

    UUID id = UUID.randomUUID();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      builder -> builder
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    UUID itemId = smallAngryPlanet.getId();

    UUID userId = usersFixture.charlotte().getId();

    IndividualResource response = loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2017, 2, 27, 10, 23, 43, UTC))
      .withReturnDate(new DateTime(2017, 3, 15, 11, 14, 36, UTC))
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

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));

    assertThat("has item enumeration",
      item.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      item.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      item.getString("volume"), is("testVolume"));

    assertThat("item status snapshot in storage is not available",
      loansStorageClient.getById(id).getJson().getString("itemStatus"),
      is("Available"));
  }

  @Test
  public void canGetALoanById() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452")
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"))
      .getId();

    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    DateTime dueDate = new DateTime(2016, 11, 15, 8, 26, 53, UTC);

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, UTC))
      .withDueDate(dueDate)
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(format("Failed to get loan: %s", getResponse.getBody()),
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

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    loanHasExpectedProperties(loan, user);
  }

  @Test
  public void canGetLoanPolicyPropertiesForMultipleLoans() {

    configClient.create(ConfigurationExample.utcTimezoneConfiguration());
    IndividualResource item1 = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource item2 = itemsFixture.basedUponNod();

    final IndividualResource user1 = usersFixture.jessica();
    final IndividualResource user2 = usersFixture.steve();

    loansFixture.checkOutByBarcode(item1, user1,
      new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    loansFixture.checkOutByBarcode(item2, user2,
      new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    final IndividualResource loanPolicy = loanPoliciesFixture.canCirculateRolling();

    MultipleJsonRecords loans = loansFixture.getAllLoans();

    loans.forEach(loanJson -> {
      loanHasLoanPolicyProperties(loanJson, loanPolicy);
      loanHasOverdueFinePolicyProperties(loanJson,
        overdueFinePoliciesFixture.facultyStandard());
      loanHasLostItemPolicyProperties(loanJson,
        lostItemFeePoliciesFixture.facultyStandard());
    });
  }

  @Test
  public void loanFoundByIdDoesNotProvideItemInformationForUnknownItem() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    final UUID loanId = loansFixture.createLoan(item, usersFixture.rebecca())
      .getId();

    itemsClient.delete(item.getId());

    Response getResponse = loansClient.getById(loanId);

    assertThat(format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void barcodeIsNotIncludedWhenItemDoesNotHaveOne() {
    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::withNoBarcode)
      .getId();

    UUID userId = usersFixture.charlotte().getId();

    loansFixture.createLoan(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, UTC))
      .withStatus("Open"));

    Response getResponse = loansClient.getById(id);

    assertThat(format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("barcode is not taken from item",
      loan.getJsonObject("item").containsKey("barcode"), is(false));
  }

  @Test
  public void loanNotFoundForUnknownId() {
    Response getResponse = loansClient.getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HTTP_NOT_FOUND));
  }

  @Test
  public void canRenewALoanByExtendingTheDueDate() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = loansFixture.createLoan(item,
      usersFixture.rebecca());

    JsonObject loanToRenew = loan.copyJson();

    DateTime dueDate = DateTime.parse(loanToRenew.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

    loanToRenew
      .put("action", "renewed")
      .put("dueDate", newDueDate.toString(ISODateTimeFormat.dateTime()))
      .put("renewalCount", 1);

    loansFixture.replaceLoan(loan.getId(), loanToRenew);

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
  public void canUpdateAClosedLoanWithoutAUserId() {
    UUID itemId = itemsFixture.basedUponNod().getId();

    final IndividualResource jessica = usersFixture.jessica();
    UUID checkinServicePointId = servicePointsFixture.cd1().getId();


    IndividualResource loan = loansFixture.createLoan(new LoanBuilder()
      .open()
      .withUserId(jessica.getId())
      .withItemId(itemId)
      .withCheckinServicePointId(checkinServicePointId));

    JsonObject updatedLoanRequest = loan.copyJson();

    updatedLoanRequest.getJsonObject("status").put("name", "Closed");
    updatedLoanRequest.remove("userId");

    loansClient.replace(loan.getId(), updatedLoanRequest);

    final JsonObject updatedLoan = loansFixture.getLoanById(loan.getId())
      .getJson();

    assertThat("Should be closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    hasNoBorrowerProperties(updatedLoan);
  }
  @Test
  public void multipleClosedLoansHaveNoBorrowerInformation() {
    UUID smallAngryPlanetId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID nodId = itemsFixture.basedUponNod().getId();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkinServicePointId2 = servicePointsFixture.cd2().getId();

    final IndividualResource user = usersFixture.jessica();

    final IndividualResource loan1 = loansClient.createAtSpecificLocation(
      new LoanBuilder()
        .withItemId(smallAngryPlanetId)
        .withCheckinServicePointId(checkinServicePointId)
        .closed()
        .withUserId(user.getId()));

    final IndividualResource loan2 = loansClient.createAtSpecificLocation(
      new LoanBuilder()
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

    MultipleJsonRecords loans = loansFixture.getAllLoans();

    loans.forEach(this::hasNoBorrowerProperties);
  }

  @Test
  public void cannotUpdateAnOpenLoanWithoutAUserId() {
    UUID itemId = itemsFixture.basedUponNod().getId();

    final IndividualResource jessica = usersFixture.jessica();

    IndividualResource loan = loansFixture.createLoan(new LoanBuilder()
      .open()
      .withUserId(jessica.getId())
      .withItemId(itemId));

    JsonObject updatedLoan = loan.copyJson();

    updatedLoan.remove("userId");

    Response putResponse = loansFixture.attemptToReplaceLoan(loan.getId(),
      updatedLoan);

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Open loan must have a user ID"),
      hasNullParameter("userId"))));
  }

  @Test
  public void updatingACurrentLoanDoesNotChangeItemStatus() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    final IndividualResource checkOutResponse = loansFixture.createLoan(item,
      usersFixture.jessica());

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    loansFixture.replaceLoan(checkOutResponse.getId(),
      checkOutResponse.getJson().copy());

    JsonObject changedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      changedItem.getJsonObject("status").getString("name"), is("Checked out"));

    Response loanFromStorage = loansStorageClient.getById(checkOutResponse.getId());

    assertThat("item status snapshot in storage is not checked out",
      loanFromStorage.getJson().getString("itemStatus"),
      is("Checked out"));
  }

  @Test
  public void loanInCollectionDoesNotProvideItemInformationForUnknownItem() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    loansFixture.createLoan(item, usersFixture.jessica());

    itemsClient.delete(item.getId());

    JsonObject loan = loansFixture.getLoans().getFirst();

    assertThat("should be no item information available",
      loan.containsKey("item"), is(false));
  }

  @Test
  public void canPageLoans() {
    IndividualResource user = usersFixture.steve();

    loansFixture.checkOutByBarcode(itemsFixture.basedUponSmallAngryPlanet(), user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponTemeraire(),user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponUprooted(),user);
    loansFixture.checkOutByBarcode(itemsFixture.basedUponInterestingTimes(),user);

    MultipleJsonRecords firstPage = loansFixture.getLoans(limit(3));
    MultipleJsonRecords secondPage = loansFixture.getLoans(limit(3),
      offset(3));

    assertThat(firstPage.size(), is(3));
    assertThat(firstPage.totalRecords(), is(5));

    assertThat(secondPage.size(), is(2));
    assertThat(secondPage.totalRecords(), is(5));

    firstPage.forEach(loan -> loanHasExpectedProperties(loan, user));
    firstPage.forEach(loan -> loanHasExpectedProperties(loan, user));

    assertThat(countOfDistinctTitles(firstPage.stream()), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPage.stream()), is(greaterThan(1)));
  }

  @Test
  public void canSearchByUserId() {
    IndividualResource firstUser = usersFixture.steve();
    UUID firstUserId = firstUser.getId();
    IndividualResource secondUser = usersFixture.jessica();
    UUID secondUserId = secondUser.getId();

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(firstUserId));

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withUserId(firstUserId));

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponTemeraire())
      .withUserId(firstUserId));

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponUprooted())
      .withUserId(secondUserId));

    loansFixture.createLoan(new LoanBuilder()
      .withItem(itemsFixture.basedUponNod())
      .withUserId(secondUserId));

    loansFixture.createLoan(new LoanBuilder().withItem(
      itemsFixture.basedUponInterestingTimes())
      .withUserId(secondUserId));

    String queryTemplate = "userId=%s";

    MultipleJsonRecords firstPage = loansFixture.getLoans(
      queryFromTemplate(queryTemplate, firstUserId));

    MultipleJsonRecords secondPage = loansFixture.getLoans(
      queryFromTemplate(queryTemplate, secondUserId));

    assertThat(firstPage.size(), is(4));
    assertThat(firstPage.totalRecords(), is(4));

    assertThat(secondPage.size(), is(3));
    assertThat(secondPage.totalRecords(), is(3));

    firstPage.forEach(loan -> loanHasExpectedProperties(loan, firstUser));
    secondPage.forEach(loan -> loanHasExpectedProperties(loan, secondUser));

    assertThat(countOfDistinctTitles(firstPage.stream()), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(secondPage.stream()), is(greaterThan(1)));
  }

  @Test
  public void canFindNoResultsFromSearch() {
    MultipleJsonRecords loans = loansFixture.getLoans(
      queryFromTemplate("userId=%s", UUID.randomUUID()));

    assertThat(loans.size(), is(0));
    assertThat(loans.totalRecords(), is(0));
  }

  @Test
  public void canFilterByLoanStatus() {
    IndividualResource user = usersFixture.charlotte();
    UUID userId = user.getId();

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponSmallAngryPlanet())
      .withRandomPastLoanDate());

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Open")
      .withItem(itemsFixture.basedUponNod())
      .withRandomPastLoanDate());

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withItem(
        itemsFixture.basedUponNod())
      .withStatus("Closed")
      .withRandomPastLoanDate());

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponTemeraire())
      .withRandomPastLoanDate());

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponUprooted())
      .withRandomPastLoanDate());

    loansFixture.createLoan(new LoanBuilder()
      .withUserId(userId)
      .withStatus("Closed")
      .withItem(itemsFixture.basedUponInterestingTimes())
      .withRandomPastLoanDate());

    String queryTemplate = "userId=\"%s\" and status.name=\"%s\"";

    MultipleJsonRecords openLoans = loansFixture.getLoans(
      queryFromTemplate(queryTemplate, userId, "Open"));

    MultipleJsonRecords closedLoans = loansFixture.getLoans(
      queryFromTemplate(queryTemplate, userId, "Closed"));

    assertThat(openLoans.size(), is(2));
    assertThat(openLoans.totalRecords(), is(2));

    assertThat(closedLoans.size(), is(4));
    assertThat(closedLoans.totalRecords(), is(4));

    openLoans.forEach(loan -> loanHasExpectedProperties(loan, user));

    closedLoans.forEach(loan -> {
        loanHasExpectedProperties(loan, user);
        hasProperty("returnDate", loan, "loan");
      });

    assertThat(countOfDistinctTitles(openLoans.stream()), is(greaterThan(1)));
    assertThat(countOfDistinctTitles(closedLoans.stream()), is(greaterThan(1)));
  }

  @Test
  public void canGetMultipleLoansWithoutUserId() {
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

    final MultipleJsonRecords multipleLoans = loansFixture.getAllLoans();

    assertThat("Should have two loans",
      multipleLoans.size(), is(2));

    multipleLoans.forEach(this::hasNoBorrowerProperties);
  }

  @Test
  public void canGetMultipleLoansForDifferentBorrowers() {
    UUID smallAngryPlanetId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID nodId = itemsFixture.basedUponNod().getId();

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkinServicePointId2 = servicePointsFixture.cd2().getId();

    final IndividualResource steveUser = usersFixture.steve();
    final IndividualResource firstLoan = loansClient.createAtSpecificLocation(
      new LoanBuilder()
        .withItemId(smallAngryPlanetId)
        .withCheckinServicePointId(checkinServicePointId)
        .closed()
        .withUserId(usersFixture.steve().getId()));

    final IndividualResource jessicaUser = usersFixture.jessica();
    final IndividualResource secondLoan = loansClient.createAtSpecificLocation(
      new LoanBuilder()
        .withItemId(nodId)
        .closed()
        .withCheckinServicePointId(checkinServicePointId2)
        .withUserId(usersFixture.jessica().getId()));

    final MultipleJsonRecords multipleLoans = loansFixture.getAllLoans();

    final Set<String> uniqueUserIds = multipleLoans.stream()
      .map(loan -> loan.getString("userId"))
      .collect(Collectors.toSet());

    assertThat("Should have different 'userId' for different loans",
      uniqueUserIds, containsInAnyOrder(jessicaUser.getId().toString(),
      steveUser.getId().toString()));

    assertThat("Should have two loans",
      multipleLoans.size(), is(2));

    loanHasExpectedProperties(firstLoan.getJson(), steveUser);
    loanHasExpectedProperties(secondLoan.getJson(), jessicaUser);
  }

  @Test
  public void canDeleteALoan() {
    final InventoryItemResource item = itemsFixture.basedUponNod();

    final UUID loanId = loansFixture.createLoan(item, usersFixture.rebecca()).getId();

    loansFixture.deleteLoan(loanId);

    Response getResponse = loansClient.getById(loanId);

    assertThat(getResponse.getStatusCode(), is(HTTP_NOT_FOUND));
  }

  @Test
  public void canCreateALoanWithServicePoints() {
    UUID loanId = UUID.randomUUID();
    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID userId = usersFixture.charlotte().getId();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkoutServicePointId = servicePointsFixture.cd2().getId();

    loansFixture.createLoan(new LoanBuilder()
      .withId(loanId)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    JsonObject loanJson = loansClient.getById(loanId).getJson();

    assertThat("loan has checkin service point id",
      loanJson.containsKey("checkinServicePointId"), is(true));
    assertThat("loan has checkout service point id",
      loanJson.containsKey("checkoutServicePointId"), is(true));
    assertThat("loan has checkin service point",
      loanJson.containsKey("checkinServicePoint"), is(true));
    assertThat("loan has checkout service point",
      loanJson.containsKey("checkoutServicePoint"), is(true));
  }

  @Test
  public void canCreateMultipleLoansWithServicePoints() {
    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkoutServicePointId = servicePointsFixture.cd2().getId();

    UUID userId = usersFixture.charlotte().getId();

    UUID loan1Id = UUID.randomUUID();
    UUID item1Id = itemsFixture.basedUponDunkirk().getId();

    UUID loan2Id = UUID.randomUUID();
    UUID item2Id = itemsFixture.basedUponSmallAngryPlanet().getId();

    UUID loan3Id = UUID.randomUUID();
    UUID item3Id = itemsFixture.basedUponUprooted().getId();

    loansFixture.createLoan(new LoanBuilder()
      .withId(loan1Id)
      .open()
      .withUserId(userId)
      .withItemId(item1Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    loansFixture.createLoan(new LoanBuilder()
      .withId(loan2Id)
      .open()
      .withUserId(userId)
      .withItemId(item2Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    loansFixture.createLoan(new LoanBuilder()
      .withId(loan3Id)
      .open()
      .withUserId(userId)
      .withItemId(item3Id)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));

    MultipleJsonRecords loanList = loansFixture.getAllLoans();

    loanList.forEach(loanJson -> {
      loanHasCheckinServicePointProperties(loanJson);
      loanHasCheckoutServicePointProperties(loanJson);
    });
  }

  @Test
  public void canGetPagedLoansWithMoreItemsThanDefaultPageLimit() {
    createLoans(50);
    queryLoans(50);
  }

  @Test
  public void canGetPagedLoansWhenIdQueryWouldExceedQueryStringLengthLimit() {
    createLoans(100);
    queryLoans(100);
  }

  private void createLoans(int total) {
    final IndividualResource mainFloor = locationsFixture.mainFloor();
    for(int i = 0; i < total; i++) {
      final IndividualResource item = itemsFixture.basedUponTemeraire(
        holdingBuilder -> holdingBuilder
          .withPermanentLocation(mainFloor)
          .withNoTemporaryLocation(),
        itemBuilder -> itemBuilder
          .withNoPermanentLocation()
          .withNoTemporaryLocation()
          .withBarcode(randomBarcode()));

      createLoan(getRandomUserId(), item.getId());
    }
  }

  private String randomBarcode() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return String.valueOf(random.nextLong(10_000_000_000L, 100_000_000_000L));
  }

  private UUID getRandomUserId() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    switch(random.nextInt(5)) {
    case 0: return usersFixture.charlotte().getId();
    case 1: return usersFixture.james().getId();
    case 2: return usersFixture.jessica().getId();
    case 3: return usersFixture.rebecca().getId();
    default: return usersFixture.steve().getId();
    }
  }

  private void createLoan(UUID userId, UUID itemId) {
    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, UTC);
    UUID checkinServicePointId = servicePointsFixture.cd1().getId();
    UUID checkoutServicePointId = servicePointsFixture.cd2().getId();
    UUID loanId = UUID.randomUUID();

    loansFixture.createLoan(new LoanBuilder()
      .withId(loanId)
      .open()
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withCheckinServicePointId(checkinServicePointId)
      .withCheckoutServicePointId(checkoutServicePointId));
  }

  private void queryLoans(int limit) {
    MultipleJsonRecords loans = loansFixture.getLoans(limit(limit));

    assertThat("Did not have expected number of loans in page",
      loans.size(), is(limit));

    loans.forEach(loan ->
      assertThat("loan does not have item", loan.containsKey("item"), is(true)));
  }

  @Test
  public void canGetAnonymizedLoan() {
    InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource individualResource = loansFixture.checkOutByBarcode(item,
      jessica, new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    JsonObject savedLoan = loansStorageClient.get(individualResource.getId())
      .getResponse().getJson();

     savedLoan.remove("userId");

     loansStorageClient.replace(individualResource.getId(), savedLoan);

     JsonObject loan = loansFixture.getLoanById(individualResource.getId()).getJson();

     loanHasPatronGroupProperties(loan, "Regular Group");
  }

  @Test
  public void canGetMultipleAnonymizedLoans() {
    InventoryItemResource firstItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource firstLoan = loansFixture.checkOutByBarcode(firstItem,
      jessica, new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    InventoryItemResource secondItem = itemsFixture.basedUponNod();
    IndividualResource henry = usersFixture.undergradHenry();
    IndividualResource secondLoan = loansFixture.checkOutByBarcode(secondItem,
      henry, new DateTime(2018, 4, 21, 11, 21, 43, UTC));

    JsonObject firstSavedLoan = loansStorageClient.get(firstLoan.getId())
      .getResponse().getJson();

    firstSavedLoan.remove("userId");

    loansStorageClient.replace(firstLoan.getId(), firstSavedLoan);

    JsonObject secondSavedLoan = loansStorageClient.get(secondLoan.getId())
      .getResponse().getJson();

    secondSavedLoan.remove("userId");

    loansStorageClient.replace(secondLoan.getId(), secondSavedLoan);

    MultipleJsonRecords loans = loansFixture.getAllLoans();

    JsonObject fetchedLoan1 = loans.getById(firstLoan.getId());
    JsonObject fetchedLoan2 = loans.getById(secondLoan.getId());

    loanHasPatronGroupProperties(fetchedLoan1, "Regular Group");
    loanHasPatronGroupProperties(fetchedLoan2, "undergrad");
  }

  private void loanHasExpectedProperties(JsonObject loan,
    IndividualResource user) {

    loanHasExpectedProperties(loan);

    if (user == null) {
      return;
    }

    JsonObject borrower = loan.getJsonObject(BORROWER);
    JsonObject personalInfo = user.getJson().getJsonObject("personal");

    hasProperty(BORROWER, loan, "loan");
    hasProperty("firstName", borrower, "borrower",
      personalInfo.getString("firstName"));

    hasProperty("lastName", borrower, "borrower",
      personalInfo.getString("lastName"));

    hasProperty("middleName", borrower, "borrower",
      personalInfo.getString("middleName"));

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

    List<JsonObject> contributors = toList(item.getJsonArray("contributors"));

    assertThat("Should have single contributor",
      contributors.size(), is(1));

    assertThat("Contributor has a name",
      contributors.get(0).containsKey("name"), is(true));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    assertTrue(loan.getJsonObject("item").containsKey(CALL_NUMBER_COMPONENTS));
    JsonObject callNumberComponents = loan
      .getJsonObject("item")
      .getJsonObject(CALL_NUMBER_COMPONENTS);

    assertThat(callNumberComponents.getString("callNumber"), is("123456"));
    assertFalse(callNumberComponents.containsKey("prefix"));
    assertThat(callNumberComponents.getString("suffix"), is("CIRC"));
  }

  protected void hasProperty(String property, JsonObject resource, String type,
    Object value) {

    assertThat(format("%s should have an %s: %s", type, property, resource),
      resource.getMap().get(property), equalTo(value));
  }

  private void hasNoBorrowerProperties(JsonObject loanJson) {
    doesNotHaveProperty("userId", loanJson, "loan");
    doesNotHaveProperty(BORROWER, loanJson, "loan");
  }

  protected void doesNotHaveProperty(String property, JsonObject resource,
    String type) {

    assertThat(format("%s should NOT have an %s: %s", type, property, resource),
      resource.getValue(property), is(nullValue()));
  }

  protected void hasProperty(String property, JsonObject resource, String type) {
    assertThat(format("%s should have an %s: %s", type, property, resource),
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

  private Integer countOfDistinctTitles(Stream<JsonObject> loans) {
    return Long.valueOf(loans
      .map(loan -> loan.getJsonObject("item").getString("title"))
      .distinct()
      .count()).intValue();
  }
}
