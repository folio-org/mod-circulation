package api.loans;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

@RunWith(JUnitParamsRunner.class)
public class DeclareLostAPITests extends APITests {
  private InventoryItemResource item;
  private JsonObject loanJson;

  @Override
  public void beforeEach() throws InterruptedException {
    super.beforeEach();
    item = itemsFixture.basedUponSmallAngryPlanet();

    loanJson = loansFixture.checkOutByBarcode(item,
      usersFixture.charlotte()).getJson();
  }

  @Test
  public void canDeclareItemLostWithComment() {
    UUID loanId = UUID.fromString(loanJson.getString("id"));
    String comment = "testing";
    DateTime dateTime = DateTime.now();

    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
        .forLoanId(loanId).on(dateTime)
        .withComment(comment);

    Response response = loansFixture.declareItemLost(loanId, builder);

    JsonObject actualLoan = loansClient.getById(loanId).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, hasLoanProperty("actionComment", comment));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", dateTime.toString()));
  }

  @Test
  public void canDeclareItemLostWithoutComment() {
    UUID loanId = UUID.fromString(loanJson.getString("id"));
    DateTime dateTime = DateTime.now();

    final DeclareItemLostRequestBuilder builder
      = new DeclareItemLostRequestBuilder()
        .forLoanId(loanId).on(dateTime)
        .withNoComment();

    Response response = loansFixture.declareItemLost(loanId, builder);

    JsonObject actualLoan = loansFixture.getLoanById(loanId).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Declared lost"));
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, hasLoanProperty("actionComment", StringUtils.EMPTY));
    assertThat(actualLoan, hasLoanProperty("declaredLostDate", dateTime.toString()));
  }

  @Test
  public void cannotDeclareItemLostForAClosedLoan() {

    UUID loanId = UUID.fromString(loanJson.getString("id"));
    DateTime dateTime = DateTime.now();

    loansFixture.checkInByBarcode(item);

    final DeclareItemLostRequestBuilder builder
      = new DeclareItemLostRequestBuilder()
        .forLoanId(loanId).on(dateTime)
        .withNoComment();

    Response response = loansFixture.attemptDeclareItemLost(loanId, builder);

    JsonObject actualLoan = loansFixture.getLoanById(loanId).getJson();
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

    final DeclareItemLostRequestBuilder builder
      = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .on(DateTime.now()).withNoComment();

    Response response = loansFixture.attemptDeclareItemLost(loanId, builder);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  @Parameters(source = DeclareLostFeeDataProvider.class, method = "feeDataProvider")
  @TestCaseName("[{index}] Charge processing fee: {0}, Processing fee: {1}, Item fee: [{2}] => {3}")
  public void lostItemFeeProperlyAssigned(boolean chargeProcessingFee, Double processingFee,
    JsonObject itemCharge, Matcher<List<JsonObject>> assignedFeesMatcher) {

    // Set-up reference data
    feeFineOwnerFixture.create(servicePointsFixture.cd1().getId());
    feeFineFixture.lostItemFee();
    feeFineFixture.lostItemProcessingFee();

    useLostItemPolicy(lostItemFeePoliciesFixture.facultyStandard(builder -> builder
      .withName("Declared lost fee test policy")
      .withChargeAmountItemPatron(chargeProcessingFee)
      .withLostItemProcessingFee(processingFee)
      .withChargeAmountItem(itemCharge)).getId());

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = loansFixture.checkOutByBarcode(item, usersFixture.charlotte());

    loansFixture.declareItemLost(loan.getId(), new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId()));

    assertThat(getAccountsForLoan(loan.getId()), assignedFeesMatcher);
  }

  @Test
  public void shouldNotAssignFeesWhenUnknownPolicy() {
    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = loansFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final JsonObject loanWithoutLostPolicy = loansStorageClient.getById(loan.getId())
      .getJson().copy();
    loanWithoutLostPolicy.remove("lostItemPolicyId");

    loansStorageClient.replace(loan.getId(), loanWithoutLostPolicy);
    assertThat(loansStorageClient.getById(loan.getId()).getJson().getString("lostItemPolicyId"),
      nullValue());

    loansFixture.declareItemLost(loan.getId(), new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId()));

    assertThat(getAccountsForLoan(loan.getId()), hasSize(0));
  }

  @Test
  public void shouldNotAssignFeesWhenNoOwnerConfigured() {
    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = loansFixture.checkOutByBarcode(item, usersFixture.charlotte());

    loansFixture.declareItemLost(loan.getId(), new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId()));

    assertThat(getAccountsForLoan(loan.getId()), hasSize(0));
  }

  @Test
  public void cannotDeclareItemLostWhenNoAutomatedLostItemFee() {
    feeFineOwnerFixture.create(servicePointsFixture.cd1().getId());
    feeFineFixture.lostItemProcessingFee();

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = loansFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = loansFixture.attemptDeclareItemLost(loan.getId(),
      new DeclareItemLostRequestBuilder()
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item fee"),
      hasParameter("feeFineType", "Lost item fee"))));
  }

  @Test
  public void cannotDeclareItemLostWhenNoAutomatedLostItemProcessingFee() {
    feeFineOwnerFixture.create(servicePointsFixture.cd1().getId());
    feeFineFixture.lostItemFee();

    final InventoryItemResource item = itemsFixture.basedUponNod();
    final IndividualResource loan = loansFixture.checkOutByBarcode(item, usersFixture.charlotte());

    final Response response = loansFixture.attemptDeclareItemLost(loan.getId(),
      new DeclareItemLostRequestBuilder()
        .forLoanId(loan.getId()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Expected automated fee of type Lost item processing fee"),
      hasParameter("feeFineType", "Lost item processing fee"))));
  }

  private List<JsonObject> getAccountsForLoan(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString()))
      .stream().collect(Collectors.toList());
  }
}
