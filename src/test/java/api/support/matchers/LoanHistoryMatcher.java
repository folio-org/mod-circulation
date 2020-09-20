package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forLoanHistoryStorage;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import api.support.MultipleJsonRecords;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanHistoryMatcher extends TypeSafeMatcher<IndividualResource> {
  private final ResourceClient loanHistoryClient;
  private final Matcher<Iterable<? extends JsonObject>> matcher;

  private LoanHistoryMatcher(Matcher<Iterable<? extends JsonObject>> matcher) {
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

  public static Matcher<IndividualResource> hasLoanHistory(
    Matcher<Iterable<? extends JsonObject>> matcher) {

    return new LoanHistoryMatcher(matcher);
  }
}
