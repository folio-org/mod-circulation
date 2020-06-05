package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forAccounts;
import static api.support.http.ResourceClient.forFeeFineActions;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import api.support.MultipleJsonRecords;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanAccountActionsMatcher extends TypeSafeDiagnosingMatcher<IndividualResource> {
  private final ResourceClient accountsClient;
  private final ResourceClient accountActionsClient;
  private final String feeFineType;
  private final Matcher<Iterable<JsonObject>> actionsMatcher;

  public LoanAccountActionsMatcher(String feeFineType, Matcher<Iterable<JsonObject>> matcher) {
    this.feeFineType = feeFineType;
    this.actionsMatcher = matcher;
    this.accountsClient = forAccounts();
    this.accountActionsClient = forFeeFineActions();
  }

  @Override
  protected boolean matchesSafely(IndividualResource loan, Description mismatchDescription) {
    final JsonObject accountForLoan = getAccountForLoan(loan.getId());
    if (accountForLoan == null) {
      mismatchDescription.appendText("No account of type: ").appendValue(feeFineType)
        .appendText(", for loan");
      return false;
    }

    return actionsMatcher.matches(getAccountActions(accountForLoan.getString("id")));
  }

  private JsonObject getAccountForLoan(UUID loanId) {
    return accountsClient.getMany(
      queryFromTemplate("loanId==\"%s\" and feeFineType==\"%s\"", loanId, feeFineType))
      .getFirst();
  }

  private MultipleJsonRecords getAccountActions(String accountId) {
    return accountActionsClient.getMany(queryFromTemplate("accountId==%s", accountId));
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Loan has account: ").appendValue(feeFineType)
      .appendText(" with actions matching: ")
      .appendDescriptionOf(actionsMatcher);
  }

  public static LoanAccountActionsMatcher hasLostItemFeeActions(
    Matcher<Iterable<JsonObject>> actionsMatcher) {

    return new LoanAccountActionsMatcher("Lost item fee", actionsMatcher);
  }

  public static Matcher<IndividualResource> hasLostItemProcessingFeeActions(
    Matcher<Iterable<JsonObject>> actionsMatcher) {

    return new LoanAccountActionsMatcher("Lost item processing fee", actionsMatcher);
  }
}
