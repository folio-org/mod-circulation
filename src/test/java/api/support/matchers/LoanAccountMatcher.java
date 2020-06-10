package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forAccounts;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanAccountMatcher extends TypeSafeMatcher<IndividualResource> {
  private final ResourceClient accountsClient;
  private final String feeFineType;
  private final Matcher<JsonObject> accountMatcher;

  public LoanAccountMatcher(String feeFineType, Matcher<JsonObject> accountMatcher) {
    this.feeFineType = feeFineType;
    this.accountMatcher = accountMatcher;
    this.accountsClient = forAccounts();
  }

  @Override
  protected boolean matchesSafely(IndividualResource loan) {
    return accountMatcher.matches(getAccountForLoan(loan.getId()));
  }

  private JsonObject getAccountForLoan(UUID loanId) {
    return accountsClient.getMany(
      queryFromTemplate("loanId==\"%s\" and feeFineType==\"%s\"", loanId, feeFineType))
      .getFirst();
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Loan has account associated matching: ")
      .appendDescriptionOf(accountMatcher);
  }

  public static LoanAccountMatcher hasLostItemFee(Matcher<JsonObject> accountMatcher) {
    return new LoanAccountMatcher("Lost item fee", accountMatcher);
  }

  public static LoanAccountMatcher hasLostItemProcessingFee(
    Matcher<JsonObject> accountMatcher) {

    return new LoanAccountMatcher("Lost item processing fee", accountMatcher);
  }

  public static LoanAccountMatcher hasOverdueFine() {
    return new LoanAccountMatcher("Overdue fine", notNullValue(JsonObject.class));
  }

  public static LoanAccountMatcher hasNoOverdueFine() {
    return new LoanAccountMatcher("Overdue fine", nullValue(JsonObject.class));
  }
}
