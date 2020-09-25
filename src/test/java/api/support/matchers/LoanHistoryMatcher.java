package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forLoanHistoryStorage;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasItem;

import java.util.UUID;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import api.support.MultipleJsonRecords;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanHistoryMatcher<T extends Iterable<?>> extends TypeSafeMatcher<IndividualResource> {
  private final ResourceClient loanHistoryClient;
  private final Matcher<T> matcher;

  private LoanHistoryMatcher(Matcher<T> matcher) {
    this.loanHistoryClient = forLoanHistoryStorage();
    this.matcher = matcher;
  }

  @Override
  protected boolean matchesSafely(IndividualResource loan) {
    return matcher.matches(getHistoryForLoan(loan.getId()));
  }

  private MultipleJsonRecords getHistoryForLoan(UUID loanId) {
    return loanHistoryClient.getMany(queryFromTemplate("loan.id=\"%s\" sortBy " +
      "createdDate/sort.descending", loanId.toString()));
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Loan has history matching: ")
      .appendDescriptionOf(matcher);
  }

  @Override
  protected void describeMismatchSafely(IndividualResource item, Description mismatchDescription) {
    mismatchDescription.appendText("was ").appendText(item.getJson().toString());
  }

  @SafeVarargs
  public static Matcher<IndividualResource> hasLoanHistoryInOrder(Matcher<JsonObject> ... matchers) {
    return new LoanHistoryMatcher<>(containsInRelativeOrder(matchers));
  }

  public static Matcher<IndividualResource> hasLoanHistoryRecord(
    Matcher<JsonObject> matcher) {

    return new LoanHistoryMatcher<>(hasItem(matcher));
  }
}
