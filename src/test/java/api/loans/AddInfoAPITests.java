package api.loans;

import api.support.APITests;
import api.support.builders.AddInfoRequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.LoanMatchers.*;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static org.folio.circulation.domain.representations.LoanProperties.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AddInfoAPITests extends APITests {

  private IndividualResource loan;
  private String loanId;

  @BeforeEach
  public void setUpItemAndLoan() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());
    loanId = loan.getId().toString();
  }

  @Test
  void canAddInfo() {

    final Response response = addInfoFixture
      .addInfo(new AddInfoRequestBuilder(loanId, "patronInfoAdded", "testing patron info"));
    assertLoanAndItem(response, "patronInfoAdded", "testing patron info");
  }

  @Test
  void addInfoLogRecordIsPublished() {

    final Response response = addInfoFixture
      .addInfo(new AddInfoRequestBuilder(loanId, "staffInfoAdded", "testing staff info"));

    assertLoanAndItem(response, "staffInfoAdded", "testing staff info");

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }


  private void assertLoanAndItem(Response response, String action, String comment ) {
    JsonObject actualLoan = loansClient.getById(UUID.fromString(loanId)).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Checked out"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty(ACTION, is(action)));
    assertThat(actualLoan, hasLoanProperty(ACTION_COMMENT, is(comment)));
  }

}
