package api.loans;

import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class DeclareLostAPITests extends APITests {
  private InventoryItemResource item;
  private JsonObject loanJson;

  @Override
  public void beforeEach()
    throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    super.beforeEach();
    item = itemsFixture.basedUponSmallAngryPlanet();

    loanJson = loansFixture.checkOutByBarcode(item,
      usersFixture.charlotte()).getJson();
  }

  @Test
  public void canDeclareItemLostWithComment()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {
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
  public void cannotDeclareItemLostForAClosedLoan()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    UUID loanId = UUID.fromString(loanJson.getString("id"));
    DateTime dateTime = DateTime.now();

    loansFixture.checkInByBarcode(item);

    final DeclareItemLostRequestBuilder builder
      = new DeclareItemLostRequestBuilder()
        .forLoanId(loanId).on(dateTime)
        .withNoComment();

    Response response = loansFixture.declareItemLost(loanId, builder);

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

    Response response = loansFixture.declareItemLost(loanId, builder);

    assertThat(response.getStatusCode(), is(404));
  }
}
