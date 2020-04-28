package api.loans;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBeforeNow;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.joda.time.Seconds.seconds;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;

@RunWith(JUnitParamsRunner.class)
public class DeclareLostAPITests extends APITests {
  @Before
  public void activatePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());
  }

  @Test
  public void canDeclareItemLostWithComment() {
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    String comment = "testing";
    DateTime dateTime = DateTime.now();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOut.getId()).on(dateTime)
      .withComment(comment);

    Response response = declareLostFixtures.declareItemLost(builder);

    JsonObject actualLoan = loansClient.getById(checkOut.getId()).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, hasLoanProperty("actionComment", comment));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", dateTime.toString()));
  }

  @Test
  public void canDeclareItemLostWithoutComment() {
    final IndividualResource checkOut = checkOutFixture
      .checkOutByBarcode(itemsFixture.basedUponNod(), usersFixture.jessica());

    DateTime dateTime = DateTime.now();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOut.getId()).on(dateTime)
      .withNoComment();

    Response response = declareLostFixtures.declareItemLost(builder);

    JsonObject actualLoan = loansFixture.getLoanById(checkOut.getId()).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, hasLoanProperty("actionComment", StringUtils.EMPTY));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", dateTime.toString()));
  }

  @Test
  public void cannotDeclareItemLostForAClosedLoan() {
    final InventoryItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    checkInFixture.checkInByBarcode(item);

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
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
  public void shouldReturn404IfLoanIsNotFound() {
    final UUID loanId = UUID.randomUUID();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .on(DateTime.now()).withNoComment();

    Response response = declareLostFixtures.attemptDeclareItemLost(builder);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void shouldChargeProcessingAndItemFeesWhenBothDefined() {
    final double expectedProcessingFee = 10.0;
    final double expectedItemFee = 20.0;
    final String expectedOwnerId = feeFineOwnerFixture.cd1Owner().getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFee()
      .withLostItemProcessingFee(expectedProcessingFee)
      .withSetCost(expectedItemFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    assertThat(loan.getJson(), isOpen());

    verifyFeeHasBeenCharged(loan.getId(), "Lost item fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item fee"),
      hasJsonPath("amount", expectedItemFee)
    ));

    verifyFeeHasBeenCharged(loan.getId(), "Lost item processing fee", allOf(
      hasJsonPath("ownerId", expectedOwnerId),
      hasJsonPath("feeFineType", "Lost item processing fee"),
      hasJsonPath("amount", expectedProcessingFee)
    ));
  }

  @Test
  public void shouldChargeItemFeeOnlyWhenNoProcessingFeeDefined() {
    final double expectedItemFee = 20.0;
    final String expectedOwnerId = feeFineOwnerFixture.cd1Owner().getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFee()
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
  public void shouldChargeProcessingFeeOnlyWhenNoItemCostDefined() {
    final double expectedProcessingFee = 10.0;
    final String expectedOwnerId = feeFineOwnerFixture.cd1Owner().getId().toString();

    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFee()
      .withLostItemProcessingFee(expectedProcessingFee)
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
  public void shouldNotChargeFeesWhenPolicyIsUnknown() {
    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final JsonObject loanWithoutLostPolicy = loansStorageClient.getById(loan.getId())
      .getJson().copy();
    loanWithoutLostPolicy.remove("lostItemPolicyId");

    loansStorageClient.replace(loan.getId(), loanWithoutLostPolicy);
    assertThat(loansStorageClient.getById(loan.getId()).getJson().getString("lostItemPolicyId"),
      nullValue());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId()));

    verifyLoanIsClosed(loan.getId());

    assertThat(getAccountsForLoan(loan.getId()), hasSize(0));
  }

  @Test
  public void cannotDeclareItemLostWhenPrimaryServicePointHasNoOwner() {
    feeFineOwnerFixture.delete(feeFineOwnerFixture.cd1Owner());

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No fee/fine owner found for item's effective location"),
      hasParameter("servicePointId", servicePointsFixture.cd1().getId().toString())
    )));
  }

  @Test
  public void cannotDeclareItemLostWhenNoAutomatedLostItemFeeTypeIsDefined() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemFee());

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .forLoanId(loan.getId()));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item fee"),
      hasParameter("feeFineType", "Lost item fee"))));
  }

  @Test
  public void cannotDeclareItemLostWhenNoAutomatedLostItemProcessingFeeTypeIsDefined() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemProcessingFee());

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = declareLostFixtures.attemptDeclareItemLost(
      new DeclareItemLostRequestBuilder()
        .forLoanId(loan.getId()));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item processing fee"),
      hasParameter("feeFineType", "Lost item processing fee"))));
  }

  @Test
  @Parameters( {
    "0",
    "10.00"
  })
  public void shouldNotAssignProcessingFeeIfDisabled(double processingFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFee()
      .withLostItemProcessingFee(processingFee)
      .withNoChargeAmountItem();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
  }

  @Test
  @Parameters( {
    "null",
    "0.0"
  })
  public void shouldNotAssignItemAnotherCostFeeIfAmountMissing(@Nullable Double itemFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFee()
      .withSetCost(itemFee);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
  }

  @Test
  public void shouldNotAssignFeeIfChargeTypeIsActualCost() {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFee()
      .withChargeAmountItem("actualCost", 10.0);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    // Loan have not to be closed because it requires manual processing.
    assertThat(loan.getJson(), isOpen());
    assertNoFeeAssignedForLoan(loan.getId());
  }

  @Test
  @Parameters( {
    "null",
    "0.0"
  })
  public void shouldNotAssignItemProcessingFeeIfAmountMissing(@Nullable Double processingFee) {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .chargeProcessingFee()
      .withLostItemProcessingFee(processingFee)
      .withNoChargeAmountItem();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
  }

  @Test
  public void canDeclareItemLostIfLostPolicyChargeAmountMissing() {
    final LostItemFeePolicyBuilder lostItemPolicy = lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName("Declared lost fee test policy")
      .doNotChargeProcessingFee()
      .withChargeAmountItem(null);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicy).getId());

    final IndividualResource loan = declareItemLost();

    verifyLoanIsClosed(loan.getId());
    assertNoFeeAssignedForLoan(loan.getId());
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

  private IndividualResource declareItemLost() {
    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd2().getId())
      .forLoanId(loan.getId()));

    return loansFixture.getLoanById(loan.getId());
  }

  private void assertNoFeeAssignedForLoan(UUID loan) {
    assertThat(getAccountsForLoan(loan), hasSize(0));
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

    assertThat(loanFromStorage, allOf(
      isClosed(),
      hasJsonPath("action", "closedLoan"),
      hasNoJsonPath("actionComment"),
      hasNoJsonPath("returnDate"),
      hasNoJsonPath("checkinServicePointId")
    ));
  }
}
