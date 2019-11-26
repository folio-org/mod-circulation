package api.loans;

import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.loanItemIsDeclaredLost;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import api.support.APITests;
import api.support.http.InterfaceUrls;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

public class DeclareLostAPITests extends APITests {
  private static final int TIMEOUT_SECONDS = 10;

  private InventoryItemResource item;
  private IndividualResource user;
  private JsonObject loanJson;
  @Override
  public void beforeEach()
    throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {
    super.beforeEach();
    item = itemsFixture.basedUponSmallAngryPlanet();
    user = usersFixture.charlotte();

    loanJson = loansFixture.checkOutByBarcode(item, user).getJson();
  }

  @Test
  public void canDeclareItemLostWithComment()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    UUID loanID = UUID.fromString(loanJson.getString("id"));
    String comment = "testing comment";

    declareItemLost(loanID.toString(), comment);

    JsonObject actualLoan = loansClient.getById(loanID).getJson();

    assertThat(actualLoan, loanItemIsDeclaredLost());
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, hasLoanProperty("actionComment", comment));
  }

  @Test
  public void canDeclareItemLostWithoutComment()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    UUID loanID = UUID.fromString(loanJson.getString("id"));

    declareItemLost(loanID.toString(), null);

    JsonObject actualLoan = loansClient.getById(loanID).getJson();

    assertThat(actualLoan, loanItemIsDeclaredLost());
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty("action", "declaredLost"));
    assertThat(actualLoan, not(hasLoanProperty("actionComment")));

  }

  private void declareItemLost(String id, String comment)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    JsonObject payload = new JsonObject();
    write(payload, "comment", comment);
    client.put(InterfaceUrls.declareLoanLostURL(id), payload, ResponseHandler.any(createCompleted));
    createCompleted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    //Without this, the loan/item is NOT fully not updated by the time the future completes!
    Thread.sleep(10);
  }

}
