package api.loans.scenarios;

import static api.support.http.InterfaceUrls.loansUrl;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;

import api.support.APITests;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class ChangeDueDateTests extends APITests {

  @Test
  public void canRenewALoanByExtendingTheDueDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource item = itemsFixture.basedUponNod();

    IndividualResource loan = loansFixture.checkOutByBarcode(item);

    JsonObject loanToChange = loan.copyJson();

    DateTime dueDate = DateTime.parse(loanToChange.getString("dueDate"));
    DateTime newDueDate = dueDate.plus(Period.days(14));

    write(loanToChange, "action", "dueDateChange");
    write(loanToChange, "dueDate", newDueDate);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(loansUrl(String.format("/%s", loan.getId())), loanToChange,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s",
      putResponse.getBody()), putResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not change due date",
      updatedLoan.getString("action"), is("dueDateChange"));

    assertThat("should not contain a return date",
      updatedLoan.containsKey("returnDate"), is(false));

    assertThat("due date does not match",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));

    assertThat("renewal count should not have changed",
      updatedLoan.containsKey("renewalCount"), is(false));

    JsonObject fetchedItem = itemsClient.getById(item.getId()).getJson();

    assertThat("item status is not checked out",
      fetchedItem.getJsonObject("status").getString("name"), is("Checked out"));

    assertThat("item status snapshot in storage is not checked out",
      loansStorageClient.getById(loan.getId()).getJson().getString("itemStatus"),
      is("Checked out"));
  }
}
