package api.loans;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.LOST_AND_PAID;
import static api.support.fakes.FakePubSub.getPublishedEvents;
import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.AccountMatchers.isClosedCancelled;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ActualCostRecordMatchers.hasAdditionalInfoForStaff;
import static api.support.matchers.ActualCostRecordMatchers.isForLoan;
import static api.support.matchers.ActualCostRecordMatchers.isInStatus;
import static api.support.matchers.EventMatchers.isValidItemDeclaredLostEvent;
import static api.support.matchers.EventMatchers.isValidLoanClosedEvent;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFees;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBeforeNow;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.folio.circulation.domain.EventType.ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.EventType.LOAN_CLOSED;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.EventType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.AgeToLostFixture.AgeToLostResult;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import api.support.matchers.EventTypeMatchers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class DeclareLostAPITests extends APITests {
  public DeclareLostAPITests() {
    super(true, true);
  }

  @BeforeEach
  public void setup() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());
  }

  @Test
  void canDeclareItemLostWithComment() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    String comment = "testing";
    ZonedDateTime dateTime = getZonedDateTime();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOut.getId()).on(dateTime)
      .withComment(comment)
      .withServicePointId(servicePointId);

    Response response = declareLostFixtures.declareItemLost(builder);

    JsonObject actualLoan = loansClient.getById(checkOut.getId()).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty("action", is("declaredLost")));
    assertThat(actualLoan, hasLoanProperty("actionComment", is(comment)));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", isEquivalentTo(dateTime)));
  }

  @Test
  void declareItemLostFailsWhenEventPublishingFailsWithBadRequestError() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOut.getId()).on(getZonedDateTime())
      .withComment("testing")
      .withServicePointId(servicePointId);

    FakePubSub.setFailPublishingWithBadRequestError(true);
    Response response = declareLostFixtures.attemptDeclareItemLost(500, builder);

    assertThat(response.getBody(), containsString(
      "Error during publishing Event Message in PubSub. Status code: 400"));
  }

  @Test
  void canDeclareItemLostWithoutComment() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    ZonedDateTime dateTime = getZonedDateTime();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOut.getId()).on(dateTime)
      .withServicePointId(servicePointId)
      .withNoComment();

    Response response = declareLostFixtures.declareItemLost(builder);

    JsonObject actualLoan = loansFixture.getLoanById(checkOut.getId()).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty("action", is("declaredLost")));
    assertThat(actualLoan, hasLoanProperty("actionComment", is(EMPTY)));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", isEquivalentTo(dateTime)));
  }

  @Test
  void cannotDeclareItemLostForAClosedLoan() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    checkInFixture.checkInByBarcode(item);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId());

    Response response = declareLostFixtures.attemptDeclareItemLost(builder);

    JsonObject actualLoan = loansFixture.getLoanById(loan.getId()).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(422));
    assertThat(actualItem, not(hasStatus("Declared lost")));
    assertThat(actualLoan, not(hasLoanProperty("action", "declaredLost")));
    assertThat(actualLoan, not(hasLoanProperty("actionComment", "declaredLost")));
    assertThat(actualLoan, not(hasLoanProperty("declaredLostDate")));
  }

  @Test
  void shouldReturn404IfLoanIsNotFound() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final UUID loanId = UUID.randomUUID();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .withServicePointId(servicePointId)
      .on(getZonedDateTime()).withNoComment();

    Response response = declareLostFixtures.attemptDeclareItemLost(builder);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  void shouldChargeProcessingAndItemFeesWhenBothDefined() {
    final double expectedProcessingFee = 10.0;
    final double expectedItemFee = 20.0;
    // permanent location SP is used, not effective location
    final String expectedOwnerId = feeFineOwnerFixture.ownerForServicePoint(
      servicePointsFixture.cd6().getId()).getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFeeWhenDeclaredLost(expectedProcessingFee)
      .withSetCost(expectedItemFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost(itemBuilder -> itemBuilder
      .withPermanentLocation(locationsFixture.fourthFloor())
      .withTemporaryLocation(locationsFixture.thirdFloor()));

    assertThat(loan.getJson(), isOpen());

    String contributorName = itemsFixture.basedUponSmallAngryPlanet().getInstance().getJson()
      .getJsonArray("contributors")
      .getJsonObject(0)
      .getString("name");

    JsonObject actualCostRecord = getActualCostRecordForLoan(loan.getId());

    verifyFeeHasBeenCharged(loan, "Lost item fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item fee"),
      hasJsonPath("amount", expectedItemFee),
      hasJsonPath("contributors[0].name", contributorName)));

    verifyFeeHasBeenCharged(loan, "Lost item processing fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item processing fee"),
      hasJsonPath("amount", expectedProcessingFee),
      hasJsonPath("contributors[0].name", contributorName)));

    assertNull(actualCostRecord);
  }

  @Test
  void shouldCreateActualCostRecordAndChargeLostItemProcessingFeeWhenDeclaredLost() {
    final double expectedProcessingFee = 10.0;
    final double expectedItemFee = 20.0;
    final String expectedOwnerId = feeFineOwnerFixture.ownerForServicePoint(
      servicePointsFixture.cd6().getId()).getId().toString();
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost with Actual Cost fee testing policy")
      .chargeProcessingFeeWhenDeclaredLost(expectedProcessingFee)
      .withActualCost(expectedItemFee);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    final IndividualResource loan = declareItemLost(itemBuilder -> itemBuilder
      .withPermanentLocation(locationsFixture.fourthFloor())
      .withTemporaryLocation(locationsFixture.thirdFloor()));

    assertThat(loan.getJson(), isOpen());

    String contributorName = itemsFixture.basedUponSmallAngryPlanet().getInstance().getJson()
      .getJsonArray("contributors")
      .getJsonObject(0)
      .getString("name");

    assertNull(getAccountForLoan(loan.getId(), "Lost item fee"));
    verifyFeeHasBeenCharged(loan, "Lost item processing fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item processing fee"),
      hasJsonPath("amount", expectedProcessingFee),
      hasJsonPath("contributors[0].name", contributorName)));
  }

  @Test
  void shouldCreateActualCostRecordWhenItemDeclaredLost() {
    final double expectedProcessingFee = 10.0;
    final double expectedItemFee = 20.0;
    final IndividualResource loanType = loanTypesFixture.canCirculate();
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989732";
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost with Actual Cost fee testing policy")
      .chargeProcessingFeeWhenDeclaredLost(expectedProcessingFee)
      .withActualCost(expectedItemFee);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue),
      itemBuilder -> itemBuilder
      .withPermanentLoanType(loanType.getId())
        .withPermanentLocation(locationsFixture.secondFloorEconomics().getId()));
    final UserResource user = usersFixture.charlotte();
    final IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(item, user);
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd2().getId())
      .forLoanId(initialLoan.getId()));
    final IndividualResource loan = loansFixture.getLoanById(initialLoan.getId());
    JsonObject actualCostRecord = getActualCostRecordForLoan(loan.getId());

    assertThat(loan.getJson(), isOpen());
    assertNotNull(actualCostRecord);
    assertThat(actualCostRecord.getString("id"), notNullValue());
    assertThat(actualCostRecord, hasJsonPath("user.id", user.getId().toString()));
    assertThat(actualCostRecord, hasJsonPath("user.barcode", user.getBarcode()));
    assertThat(actualCostRecord, hasJsonPath("loan.id", loan.getId().toString()));
    assertThat(actualCostRecord, hasJsonPath("lossType", "Declared lost"));
    assertThat(actualCostRecord.getString("lossDate"), notNullValue());
    assertThat(actualCostRecord, hasJsonPath("instance.title",
      item.getInstance().getJson().getString("title")));

    JsonArray identifiers = actualCostRecord.getJsonObject("instance").getJsonArray("identifiers");
    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      Is.is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"), Is.is(isbnValue));
    assertThat(identifiers.getJsonObject(0).getString("identifierType"),
      Is.is("ISBN"));

    assertThat(actualCostRecord, hasJsonPath("item.barcode", item.getBarcode()));
    assertThat(actualCostRecord, hasJsonPath("item.loanType", loanType.getJson().getString("name")));

    JsonObject actualCallNumberComponents = item.getJson()
      .getJsonObject("effectiveCallNumberComponents");
    JsonObject callNumberComponentsToCompare = actualCostRecord.getJsonObject("item")
      .getJsonObject("effectiveCallNumberComponents");
    assertThat(callNumberComponentsToCompare,
      hasJsonPath("callNumber", actualCallNumberComponents.getString("callNumber")));
    assertThat(callNumberComponentsToCompare,
      hasJsonPath("prefix", actualCallNumberComponents.getString("prefix")));
    assertThat(callNumberComponentsToCompare,
      hasJsonPath("suffix", actualCallNumberComponents.getString("suffix")));

    assertThat(actualCostRecord, hasJsonPath("item.permanentLocation","2nd Floor - Economics"));
    assertThat(actualCostRecord, hasJsonPath("feeFine.ownerId", notNullValue()));
    assertThat(actualCostRecord, hasJsonPath("feeFine.owner", notNullValue()));
    assertThat(actualCostRecord, hasJsonPath("feeFine.typeId", notNullValue()));
    assertThat(actualCostRecord, hasJsonPath("feeFine.type", "Lost item fee (actual cost)"));
    assertThat(actualCostRecord, hasNoJsonPath("accountId"));
  }

  @Test
  void shouldCreateActualCostRecordWithEmptyIdentifiersWhenTheyNotExistInInstance() {
    final IndividualResource loanType = loanTypesFixture.canCirculate();
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost with Actual Cost fee testing policy")
      .chargeProcessingFeeWhenDeclaredLost(10.0)
      .withActualCost(20.0);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withPermanentLoanType(loanType.getId())
        .withTemporaryLocation(locationsFixture.mainFloor().getId()));
    final IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(item,
      usersFixture.charlotte());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd2().getId())
      .forLoanId(initialLoan.getId()));
    final IndividualResource loan = loansFixture.getLoanById(initialLoan.getId());
    JsonObject actualCostRecord = getActualCostRecordForLoan(loan.getId());

    assertThat(loan.getJson(), isOpen());
    assertNotNull(actualCostRecord);
    assertThat(actualCostRecord.getString("id"), notNullValue());

    JsonArray identifiers = item.getInstance().getJson().getJsonArray("identifiers");
    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.stream().toArray(), emptyArray());
  }

  @Test
  void shouldCreateRecordAndNotChargeProcessingFeeWhenLostItemPolicySetToActualCostOnly() {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost with Actual Cost fee testing policy")
      .withActualCost(20.0);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withPermanentLoanType(loanTypesFixture.canCirculate().getId())
        .withTemporaryLocation(locationsFixture.mainFloor().getId()));
    final UserResource user = usersFixture.charlotte();
    final IndividualResource initialLoan = checkOutFixture.checkOutByBarcode(item, user);
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd2().getId())
      .forLoanId(initialLoan.getId()));
    final IndividualResource loan = loansFixture.getLoanById(initialLoan.getId());
    JsonObject actualCostRecord = getActualCostRecordForLoan(loan.getId());

    assertThat(loan.getJson(), isOpen());
    assertNotNull(actualCostRecord);
    assertThat(actualCostRecord.getString("id"), notNullValue());
    assertNull(getAccountForLoan(loan.getId(), "Lost item fee"));
    assertNull(getAccountForLoan(loan.getId(), "Lost item processing fee"));
  }

  @Test
  void shouldChargeItemFeeOnlyWhenNoProcessingFeeDefined() {
    final double expectedItemFee = 20.0;
    final String expectedOwnerId = feeFineOwnerFixture.cd1Owner().getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withSetCost(expectedItemFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    assertThat(loan.getJson(), isOpen());

    verifyFeeHasBeenCharged(loan, "Lost item fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item fee"),
      hasJsonPath("amount", expectedItemFee)
    ));
  }

  @Test
  void shouldChargeProcessingFeeOnlyWhenNoItemCostDefined() {
    final double expectedProcessingFee = 10.0;
    final String expectedOwnerId = feeFineOwnerFixture.cd1Owner().getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFeeWhenDeclaredLost(expectedProcessingFee)
      .withSetCost(0.0);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    assertThat(loan.getJson(), isOpen());

    verifyFeeHasBeenCharged(loan, "Lost item processing fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item processing fee"),
      hasJsonPath("amount", expectedProcessingFee)
    ));
  }

  @Test
  void shouldNotChargeFeesWhenPolicyIsUnknown() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final ItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final JsonObject loanWithoutLostPolicy = loansStorageClient.getById(loan.getId())
      .getJson().copy();
    loanWithoutLostPolicy.remove("lostItemPolicyId");

    loansStorageClient.replace(loan.getId(), loanWithoutLostPolicy);
    assertThat(loansStorageClient.getById(loan.getId()).getJson().getString("lostItemPolicyId"),
      nullValue());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId())
      .withServicePointId(servicePointId));

    verifyLoanIsClosed(loan.getId());

    assertThat(getAccountsForLoan(loan.getId()), hasSize(0));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());

    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyThatFirstPublishedLoanClosedEventIsValid(loan);
  }

  @Test
  void cannotDeclareItemLostWhenPrimaryServicePointHasNoOwner() {
    feeFineOwnerFixture.cleanUp();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource permanentLocation = locationsFixture.thirdFloor();
    final ItemResource item = itemsFixture.basedUponNod(
      builder -> builder.withPermanentLocation(permanentLocation));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .withServicePointId(servicePointId)
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No fee/fine owner found for item's permanent location"),
      hasParameter("locationId", permanentLocation.getId().toString())
    )));
    assertThat(itemsFixture.getById(item.getId()), hasItemStatus(CHECKED_OUT));
  }

  @Test
  void canDeclareLostIfNotChargeableLostItemPolicyAndNoOwner() {
    feeFineOwnerFixture.cleanUp();
    LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withNoChargeAmountItem();
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    ItemResource item = itemsFixture.basedUponNod();
    var loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve()).getJson();
    declareLostFixtures.declareItemLost(loan);

    assertThat(itemsFixture.getById(item.getId()), hasItemStatus(LOST_AND_PAID));
  }

  @Test
  void cannotDeclareLostIfActualCostLostItemPolicyAndNoOwner() {
    feeFineOwnerFixture.cleanUp();
    LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withActualCost(0.0);
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource permanentLocation = locationsFixture.thirdFloor();
    ItemResource item = itemsFixture.basedUponNod();
    var loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());
    Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .withServicePointId(servicePointId)
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No fee/fine owner found for item's permanent location"),
      hasParameter("locationId", permanentLocation.getId().toString())
    )));
    assertThat(itemsFixture.getById(item.getId()), hasItemStatus(CHECKED_OUT));
  }

  @Test
  void cannotDeclareItemLostWhenNoAutomatedLostItemFeeTypeIsDefined() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemFee());

    final ItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .withServicePointId(servicePointId)
        .forLoanId(loan.getId()));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item fee"),
      hasParameter("feeFineType", "Lost item fee"))));
  }

  @Test
  void cannotDeclareItemLostWhenNoAutomatedLostItemProcessingFeeTypeIsDefined() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemProcessingFee());
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final ItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .withServicePointId(servicePointId)
        .forLoanId(loan.getId()));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item processing fee"),
      hasParameter("feeFineType", "Lost item processing fee"))));
  }

  @Test
  public void cannotDeclareItemLostWithoutServicePointId() {
	 final ItemResource item = itemsFixture.basedUponNod();
	 final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

	 final Response response = declareLostFixtures.attemptDeclareItemLost(
			new DeclareItemLostRequestBuilder()
	      .forLoanId(loan.getId()));

	  assertThat(response.getStatusCode(), is(422));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "0",
    "10.00"
  })
  void shouldNotAssignProcessingFeeIfDisabled(double processingFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withLostItemProcessingFee(processingFee)
      .withNoChargeAmountItem();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());

    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyThatFirstPublishedLoanClosedEventIsValid(loan);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
    "0.0"
  })
  void shouldNotAssignItemSetCostFeeIfAmountMissing(Double itemFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withSetCost(itemFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
    assertThatPublishedLoanLogRecordEventsAreValid(loan.getJson());

    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyThatFirstPublishedLoanClosedEventIsValid(loan);
  }

  @Test
  void shouldNotAutomaticallyChargeActualCostFeeToPatron() {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withActualCost(10.0);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    // Loan have not to be closed because it requires manual processing.
    assertThat(loan.getJson(), isOpen());
    assertNoFeeAssignedForLoan(loan.getId());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
    "0.0"
  })
  void shouldNotAssignItemProcessingFeeIfAmountMissing(Double processingFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFeeWhenDeclaredLost(processingFee)
      .withNoChargeAmountItem();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
    assertThatPublishedLoanLogRecordEventsAreValid(loan.getJson());

    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyThatFirstPublishedLoanClosedEventIsValid(loan);
  }

  @Test
  void canDeclareItemLostIfLostPolicyItemFeeAmountMissing() {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withChargeAmountItem(null);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
    assertThatPublishedLoanLogRecordEventsAreValid(loan.getJson());

    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyThatFirstPublishedLoanClosedEventIsValid(loan);
  }

  @Test
  void declaredLostEventIsPublished() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource loanIndividualResource = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanIndividualResource.getId())
      .on(getZonedDateTime())
      .withServicePointId(servicePointId)
      .withNoComment();
    declareLostFixtures.declareItemLost(builder);

    // There should be five events published - "check out", "log event", "declared lost"
    // and one "log record"
    final var publishedEvents = Awaitility.await()
      .atMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(4));

    final var event = publishedEvents.findFirst(byEventType(EventTypeMatchers.ITEM_DECLARED_LOST));
    final var loan = loanIndividualResource.getJson();

    assertThat(event, isValidItemDeclaredLostEvent(loan));
    verifyNumberOfPublishedEvents(LOAN_CLOSED, 0);

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(UUID.fromString(loan.getString("id"))).getJson());
  }

  @Test
  void cannotDeclareItemLostTwice() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource loan = declareItemLost();
    final UUID itemId = UUID.fromString(loan.getJson().getString("itemId"));

    assertThat(itemsClient.getById(itemId).getJson(), isDeclaredLost());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .withServicePointId(servicePointId)
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The item is already declared lost"),
      hasParameter("itemId", itemId.toString()))));
  }

  @Test
  public void shouldCancelUnpaidLostItemFeesWhenItemDeclaredLostAndFeesAlreadyApplied() {
    final double expectedProcessingFee = 5.0;
    final double expectedItemFee = 10.0;
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final LostItemFeePolicyBuilder lostPolicyBuilder = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("age to lost with fees")
        .billPatronImmediatelyWhenAgedToLost()
        .withSetCost(expectedItemFee)
        .withLostItemProcessingFee(expectedProcessingFee)
        .withNoFeeRefundInterval()
        .withChargeAmountItemPatron(false)
        .withChargeAmountItemSystem(true);

    lostItemFeePoliciesFixture.create(lostPolicyBuilder).getJson();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostPolicyBuilder).getId());

    AgeToLostResult agedToLostLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(lostPolicyBuilder);

    JsonObject item = itemsFixture.getById(agedToLostLoan.getItemId()).getJson();
    loansClient.getById(agedToLostLoan.getLoanId()).getJson();

    assertThat(item, isAgedToLost());

    JsonObject itemFee = getAccountForLoan(agedToLostLoan.getLoanId(), "Lost item fee");
    JsonObject itemProcessingFee = getAccountForLoan(agedToLostLoan.getLoanId(), "Lost item processing fee");

    assertThat(itemFee, hasJsonPath("amount", expectedItemFee));
    assertThat(itemProcessingFee, hasJsonPath("amount", expectedProcessingFee));

    final ZonedDateTime declareLostDate = getZonedDateTime().plusWeeks(1);
    mockClockManagerToReturnFixedDateTime(declareLostDate);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(agedToLostLoan.getLoanId())
      .withServicePointId(servicePointId)
      .on(declareLostDate)
      .withNoComment();

    declareLostFixtures.declareItemLost(builder);

    JsonObject declareLostLoan = loansClient.getById(agedToLostLoan.getLoanId()).getJson();
    JsonObject declareLostItem = declareLostLoan.getJsonObject("item");

    assertThat(declareLostItem, isDeclaredLost());

    Double amountRemaining = declareLostLoan.getJsonObject("feesAndFines").getDouble("amountRemainingToPay");
    assertEquals(amountRemaining, 10.0, 0.01);

    List<JsonObject> fees = getAccountsForLoan(agedToLostLoan.getLoanId());

    assertThat(fees, hasSize(3));
    assertThat(getOpenAccounts(fees), hasSize(1));
  }

  @Test
  public void shouldRefundPartiallyPaidOrTransferredLostItemFeesBeforeApplyingNewFees() {
    final double expectedItemFee = 20.0;
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final LostItemFeePolicyBuilder lostPolicyBuilder = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("age to lost with fees")
        .billPatronImmediatelyWhenAgedToLost()
        .withSetCost(expectedItemFee)
        .withNoFeeRefundInterval()
        .withChargeAmountItemPatron(false)
        .withChargeAmountItemSystem(false);

    lostItemFeePoliciesFixture.create(lostPolicyBuilder).getJson();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostPolicyBuilder).getId());

    AgeToLostResult agedToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFees(lostPolicyBuilder);
    UUID testLoanId = agedToLostResult.getLoanId();
    UUID itemId = agedToLostResult.getItemId();

    loansClient.getById(testLoanId).getJson();
    JsonObject AgeToLostItem = itemsFixture.getById(itemId).getJson();

    assertThat(AgeToLostItem, isAgedToLost());

    JsonObject itemFee = getAccountForLoan(testLoanId, "Lost item fee");

    assertThat(itemFee, hasJsonPath("amount", expectedItemFee));

    feeFineAccountFixture.transferLostItemFee(testLoanId, 5.00);
    feeFineAccountFixture.payLostItemFee(testLoanId, 5.00);

    JsonObject transferredAndPaidLoan = loansClient.getById(testLoanId).getJson();
    JsonObject transferredAndPaidItemFee = getAccountForLoan(testLoanId, "Lost item fee");

    assertThat(getAccountsForLoan(testLoanId), hasSize(1));
    assertThat(transferredAndPaidItemFee, hasJsonPath("remaining", 10.00));

    Double amountRemaining = transferredAndPaidLoan.getJsonObject("feesAndFines").getDouble("amountRemainingToPay");
    assertEquals(amountRemaining, 10.0, 0.01);

    final ZonedDateTime declareLostDate = getZonedDateTime().plusWeeks(1);
    mockClockManagerToReturnFixedDateTime(declareLostDate);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(testLoanId)
      .withServicePointId(servicePointId)
      .on(declareLostDate)
      .withNoComment();

    declareLostFixtures.declareItemLost(builder);

    JsonObject declareLostLoan = loansClient.getById(testLoanId).getJson();
    JsonObject declareLostItem = itemsFixture.getById(itemId).getJson();

    assertThat(declareLostItem, isDeclaredLost());

    Double finalAmountRemaining = declareLostLoan.getJsonObject("feesAndFines").getDouble("amountRemainingToPay");
    assertEquals(finalAmountRemaining, 20.0, 0.01);

    List<JsonObject> accounts = getAccountsForLoan(testLoanId);

    assertThat(accounts, hasSize(2));
    assertThat(getOpenAccounts(accounts), hasSize(1));
  }

  @Test
  void shouldClearExistingFeesAndCloseLoanAsLostAndPaidIfLostandPaidItemDeclaredLostAndPolicySetNotToChargeFees() {
    final double lostItemProcessingFee = 20.0;
    UUID servicePointId = servicePointsFixture.cd1().getId();

    final LostItemFeePolicyBuilder lostPolicyBuilder = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("age to lost with processing fees")
      .billPatronImmediatelyWhenAgedToLost()
      .withNoFeeRefundInterval()
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .chargeProcessingFeeWhenAgedToLost(lostItemProcessingFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostPolicyBuilder).getId());

    AgeToLostResult agedToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFees(lostPolicyBuilder);
    UUID testLoanId = agedToLostResult.getLoanId();
    UUID itemId = agedToLostResult.getItemId();

    JsonObject AgeToLostItem = itemsFixture.getById(itemId).getJson();

    assertThat(AgeToLostItem, isAgedToLost());

    JsonObject itemFee = getAccountForLoan(testLoanId, "Lost item processing fee");

    assertThat(itemFee, hasJsonPath("amount", lostItemProcessingFee));

    final ZonedDateTime declareLostDate = getZonedDateTime().plusWeeks(1);
    mockClockManagerToReturnFixedDateTime(declareLostDate);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(testLoanId)
      .withServicePointId(servicePointId)
      .on(declareLostDate)
      .withNoComment();

    FakePubSub.clearPublishedEvents();

    declareLostFixtures.declareItemLost(builder);

    JsonObject declareLostLoan = loansClient.getById(testLoanId).getJson();
    JsonObject declareLostItem = itemsFixture.getById(itemId).getJson();

    assertThat(declareLostItem, isLostAndPaid());

    Double finalAmountRemaining = declareLostLoan.getJsonObject("feesAndFines").getDouble("amountRemainingToPay");
    assertEquals(finalAmountRemaining, 0.0, 0.01);

    List<JsonObject> accounts = getAccountsForLoan(testLoanId);

    assertThat(accounts, hasSize(1));
    assertThat(getOpenAccounts(accounts), hasSize(0));

    verifyNumberOfPublishedEvents(LOAN_CLOSED, 1);
    verifyNumberOfPublishedEvents(ITEM_DECLARED_LOST, 0);
    verifyThatFirstPublishedLoanClosedEventIsValid(declareLostLoan);
  }

  @Test
  void shouldNotChargeOverdueFeesDuringCheckInWhenItemDeclaredLostAndRefundFeePeriodHasPassed() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    IndividualResource overduePolicy = overdueFinePoliciesFixture.facultyStandard();
    IndividualResource lostItemPolicy = lostItemFeePoliciesFixture.ageToLostAfterOneWeek();

    policiesActivation.use(PoliciesToActivate.builder()
      .lostItemPolicy(lostItemPolicy)
      .overduePolicy(overduePolicy));

    IndividualResource loan = checkOutFixture
      .checkOutByBarcode(item, usersFixture.jessica());

    // advance system time by five weeks to accrue fines before declared lost
    final ZonedDateTime declareLostDate = getZonedDateTime().plusWeeks(5);
    mockClockManagerToReturnFixedDateTime(declareLostDate);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId())
      .withServicePointId(servicePointId)
      .on(declareLostDate)
      .withNoComment();
    declareLostFixtures.declareItemLost(builder);

    final ZonedDateTime checkInDate = getZonedDateTime().plusWeeks(6);
    mockClockManagerToReturnFixedDateTime(checkInDate);
    checkInFixture.checkInByBarcode(item, checkInDate);

    assertThat(loansFixture.getLoanById(loan.getId()), hasNoOverdueFine());
  }

  @Test
  void shouldCreateNoteWhenItemDeclaredLostAfterBeingClaimedReturned() {
    String comment = "testing";

    assertThat(notesClient.getAll().size(), is(0));
    UUID servicePointId = servicePointsFixture.cd1().getId();

    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID loanId = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte())
      .getId();

    claimItemReturnedFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(loanId)
      .withItemClaimedReturnedDate(getZonedDateTime()));

    ZonedDateTime dateTime = getZonedDateTime();

    JsonObject updatedLoan = loansClient.get(loanId).getJson();
    assertThat(updatedLoan.getJsonObject("item"), hasStatus("Claimed returned"));

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId).on(dateTime)
      .withServicePointId(servicePointId)
      .withComment(comment);

    declareLostFixtures.declareItemLost(builder);

    assertNoteHasBeenCreated();
  }

  @Test
  void shouldNotCreateNoteWhenNotPreviouslyClaimedReturned() {
    String comment = "testing";
    UUID servicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UUID loanId = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte())
      .getId();

    ZonedDateTime dateTime = getZonedDateTime();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId).on(dateTime)
      .withServicePointId(servicePointId)
      .withComment(comment);

    declareLostFixtures.declareItemLost(builder);

    assertEquals(0, notesClient.getAll().size());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "0.0, 1",
    "0.0, 9999999",
    "1.0, 1",
    "1.0, 9999999"
  })
  void shouldCancelActualCostLostItemFee(double processingFeeAmount, int feeRefundIntervalMinutes) {
    LostItemFeePolicyBuilder policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("test policy")
      .withActualCost(0.0)
      .withLostItemProcessingFee(processingFeeAmount)
      .withChargeAmountItemSystem(true) // charge processing fee when item is aged to lost
      .withChargeAmountItemPatron(true) // charge processing fee when item is declared lost
      .withFeeRefundInterval(Period.minutes(feeRefundIntervalMinutes));

    AgeToLostResult ageToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    IndividualResource loan = ageToLostResult.getLoan();
    UUID loanId = ageToLostResult.getLoanId();

    List<JsonObject> actualCostRecordsAfterAgingToLost = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsAfterAgingToLost, hasSize(1));
    JsonObject actualCostRecordBeforeDeclaringLost = actualCostRecordsAfterAgingToLost.get(0);
    assertThat(actualCostRecordBeforeDeclaringLost, isInStatus(OPEN));

    assertThat(loan, hasNoLostItemFee());
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());

    // should cancel initial processing fee and actual cost record regardless of fee refund interval
    declareLostFixtures.declareItemLost(loanId);

    List<JsonObject> actualCostRecordsAfterDeclaringLost = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsAfterDeclaringLost, hasSize(2));

    for (JsonObject actualCostRecord : actualCostRecordsAfterDeclaringLost) {
      assertThat(actualCostRecord, isForLoan(loanId));
      if (actualCostRecord.getString("id").equals(actualCostRecordBeforeDeclaringLost.getString("id"))) {
        assertThat(actualCostRecord, isInStatus(CANCELLED));
        assertThat(actualCostRecord, hasAdditionalInfoForStaff("Aged to lost item was declared lost"));
      } else {
        assertThat(actualCostRecord, isInStatus(OPEN));
      }
    }

    if (processingFeeAmount > 0) {
      assertThat(loan, hasLostItemProcessingFees(hasItems(
        isOpen(processingFeeAmount),
        isClosedCancelled("Cancelled item declared lost", processingFeeAmount)
      )));
    } else {
      assertThat(loan, hasNoLostItemProcessingFee());
    }
  }

  private List<JsonObject> getAccountsForLoan(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString()))
      .stream().collect(Collectors.toList());
  }

  private JsonObject getAccountForLoan(UUID loanId, String feeType) {
    return accountsClient.getMany(queryFromTemplate("loanId==%s and feeFineType==%s",
      loanId.toString(), feeType))
      .getFirst();
  }

  private JsonObject getActualCostRecordForLoan(UUID loanId) {
    return actualCostRecordsClient.getAll().stream()
      .filter(record -> record.getJsonObject("loan").getString("id").equals(loanId.toString()))
      .findFirst()
      .orElse(null);
  }

  private IndividualResource declareItemLost(UnaryOperator<ItemBuilder> itemBuilder) {
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd2().getId())
      .forLoanId(loan.getId()));

    return loansFixture.getLoanById(loan.getId());
  }

  private IndividualResource declareItemLost() {
    return declareItemLost(UnaryOperator.identity());
  }

  private void assertNoFeeAssignedForLoan(UUID loan) {
    assertThat(getAccountsForLoan(loan), hasSize(0));
  }

  private List<JsonObject> getOpenAccounts(List<JsonObject> accounts) {
    return accounts.stream().filter(this::isAccountOpen).collect(Collectors.toList());
  }

  private Boolean isAccountOpen(JsonObject account) {
    if (
      account.getJsonObject("status").getString("name").equals("Open")  &&
      account.getJsonObject("paymentStatus").getString("name").equals("Outstanding")  &&
      account.getDouble("remaining") > 0.0
    ) {
      return true;
    } else {
      return false;
    }
  }

  private void verifyFeeHasBeenCharged(IndividualResource loan, String feeType,
    Matcher<JsonObject> accountMatcher) {

    final JsonObject account = getAccountForLoan(loan.getId(), feeType);

    assertThat(account, accountMatcher);

    if (account == null) {
      return;
    }
    assertThat(account.getString("loanPolicyId"), is(loan.getJson().getString("loanPolicyId")));
    assertThat(account.getString("overdueFinePolicyId"), is(loan.getJson().getString(
      "overdueFinePolicyId")));
    assertThat(account.getString("lostItemFeePolicyId"), is(loan.getJson().getString(
      "lostItemPolicyId")));

    final JsonObject action = feeFineActionsClient
      .getMany(queryFromTemplate("accountId==%s", account.getString("id")))
      .getFirst();

    assertThat(action, notNullValue());
    assertThat(action, allOf(
      hasJsonPath("amountAction", account.getDouble("amount")),
      hasJsonPath("balance", account.getDouble("amount")),
      hasJsonPath("userId", account.getString("userId")),
      hasJsonPath("createdAt", servicePointsFixture.cd2().getJson().getString("id")),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("typeAction", feeType),
      hasJsonPath("dateAction", withinSecondsBeforeNow(1))
    ));
  }

  private void verifyLoanIsClosed(UUID loanId) {
    final JsonObject loanFromStorage = loansFixture.getLoanById(loanId).getJson();
    final JsonObject itemFromStorage = itemsClient.getById(
      UUID.fromString(loanFromStorage.getString("itemId"))).getJson();

    assertThat(loanFromStorage, allOf(
      isClosed(),
      hasJsonPath("action", "closedLoan"),
      hasNoJsonPath("actionComment"),
      hasNoJsonPath("returnDate"),
      hasNoJsonPath("checkinServicePointId")
    ));

    assertThat(itemFromStorage, isLostAndPaid());

    verifyDeclaredLostHistoryRecordCreated(loanId);
  }

  private void verifyDeclaredLostHistoryRecordCreated(UUID loanId) {
    final MultipleJsonRecords loanHistory = loanHistoryClient
      .getMany(queryFromTemplate("loan.id==%s and operation==U", loanId));

    assertThat(loanHistory, hasItems(
      allOf(
        hasJsonPath("loan.action", "declaredLost"),
        hasJsonPath("loan.itemStatus", "Declared lost")),
      allOf(
        hasJsonPath("loan.status.name", "Closed"),
        hasJsonPath("loan.action", "closedLoan"),
        hasJsonPath("loan.itemStatus", "Lost and paid"))
    ));
  }

  private void assertNoteHasBeenCreated() {
    List<JsonObject> notes = notesClient.getAll();
    assertThat(notes.size(), is(1));
    assertThat(notes.get(0).getString("title"), is("Claimed returned item marked declared lost"));
    assertThat(notes.get(0).getString("domain"), is("users"));
  }

  private static void verifyNumberOfPublishedEvents(EventType eventType, int eventCount) {
    assertThat(getPublishedEventsAsList(byEventType(eventType.toString())), hasSize(eventCount));
  }

  private static void verifyThatFirstPublishedLoanClosedEventIsValid(IndividualResource loan) {
    verifyThatFirstPublishedLoanClosedEventIsValid(loan.getJson());
  }

  private static void verifyThatFirstPublishedLoanClosedEventIsValid(JsonObject loan) {
    assertThat(
      getPublishedEvents().findFirst(byEventType(LOAN_CLOSED.toString())),
      isValidLoanClosedEvent(loan));
  }
}
