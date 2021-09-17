package api.loans;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.fakes.FakePubSub.getPublishedEvents;
import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.EventMatchers.isValidItemDeclaredLostEvent;
import static api.support.matchers.EventMatchers.isValidLoanClosedEvent;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
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
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.domain.EventType.ITEM_DECLARED_LOST;
import static org.folio.circulation.domain.EventType.LOAN_CLOSED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.joda.time.Seconds.seconds;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.EventType;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import api.support.matchers.EventTypeMatchers;
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
    DateTime dateTime = ClockUtil.getDateTime();

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
  void canDeclareItemLostWithoutComment() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    DateTime dateTime = ClockUtil.getDateTime();

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
      .on(ClockUtil.getDateTime()).withNoComment();

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

    verifyFeeHasBeenCharged(loan.getId(), "Lost item fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item fee"),
      hasJsonPath("amount", expectedItemFee),
      hasJsonPath("contributors[0].name", contributorName)));

    verifyFeeHasBeenCharged(loan.getId(), "Lost item processing fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item processing fee"),
      hasJsonPath("amount", expectedProcessingFee),
      hasJsonPath("contributors[0].name", contributorName)));
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

    verifyFeeHasBeenCharged(loan.getId(), "Lost item fee", allOf(
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

    verifyFeeHasBeenCharged(loan.getId(), "Lost item processing fee", allOf(
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
      .on(ClockUtil.getDateTime())
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

    final DateTime declareLostDate = ClockUtil.getDateTime().plusWeeks(1);
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

    final DateTime declareLostDate = ClockUtil.getDateTime().plusWeeks(1);
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

	  final DateTime declareLostDate = ClockUtil.getDateTime().plusWeeks(1);
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
    final DateTime declareLostDate = ClockUtil.getDateTime().plusWeeks(5);
    mockClockManagerToReturnFixedDateTime(declareLostDate);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId())
      .withServicePointId(servicePointId)
      .on(declareLostDate)
      .withNoComment();
    declareLostFixtures.declareItemLost(builder);

    final DateTime checkInDate = ClockUtil.getDateTime().plusWeeks(6);
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
      .withItemClaimedReturnedDate(ClockUtil.getDateTime()));

    DateTime dateTime = ClockUtil.getDateTime();

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

    DateTime dateTime = ClockUtil.getDateTime();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId).on(dateTime)
      .withServicePointId(servicePointId)
      .withComment(comment);

    declareLostFixtures.declareItemLost(builder);

    assertEquals(0, notesClient.getAll().size());
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

  private void verifyFeeHasBeenCharged(UUID loanId, String feeType,
    Matcher<JsonObject> accountMatcher) {

    final JsonObject account = getAccountForLoan(loanId, feeType);

    assertThat(account, accountMatcher);

    if (account == null) {
      return;
    }

    final JsonObject action = feeFineActionsClient
      .getMany(queryFromTemplate("accountId==%s", account.getString("id")))
      .getFirst();

    assertThat(action, notNullValue());
    assertThat(action, allOf(
      hasJsonPath("amountAction", account.getDouble("amount")),
      hasJsonPath("balance", account.getDouble("amount")),
      hasJsonPath("userId", account.getString("userId")),
      hasJsonPath("createdAt", servicePointsFixture.cd2().getJson().getString("name")),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("typeAction", feeType),
      hasJsonPath("dateAction", withinSecondsBeforeNow(seconds(1)))
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
