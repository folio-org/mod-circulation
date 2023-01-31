package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forAccounts;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import api.support.MultipleJsonRecords;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanAccountMatcher extends TypeSafeMatcher<IndividualResource> {
  private final ResourceClient accountsClient;
  private final String feeFineType;
  private final Matcher<Iterable<JsonObject>> accountsMatcher;

  public LoanAccountMatcher(String feeFineType, Matcher<Iterable<JsonObject>> accountsMatcher) {
    this.feeFineType = feeFineType;
    this.accountsMatcher = accountsMatcher;
    this.accountsClient = forAccounts();
  }

  @Override
  protected boolean matchesSafely(IndividualResource loan) {
    return accountsMatcher.matches(getAccountForLoan(loan.getId()));
  }

  private MultipleJsonRecords getAccountForLoan(UUID loanId) {
    return accountsClient.getMany(
      queryFromTemplate("loanId==\"%s\" and feeFineType==\"%s\"", loanId, feeFineType));
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Loan has accounts of type: ")
      .appendValue(feeFineType)
      .appendText(" that matches: ")
      .appendDescriptionOf(accountsMatcher);
  }

  public static LoanAccountMatcher hasLostItemFee(Matcher<JsonObject> accountMatcher) {
    return hasLostItemFees(hasItems(accountMatcher));
  }

  public static LoanAccountMatcher hasLostItemFeeActualCost(Matcher<JsonObject> accountMatcher) {
    return hasLostItemFeesActualCost(hasItems(accountMatcher));
  }

  public static LoanAccountMatcher hasNoLostItemFee() {
    return hasLostItemFees(iterableWithSize(0));
  }

  public static LoanAccountMatcher hasLostItemFees(Matcher<Iterable<JsonObject>> accountsMatcher) {
    return new LoanAccountMatcher("Lost item fee", accountsMatcher);
  }

  public static LoanAccountMatcher hasLostItemFeesActualCost(
    Matcher<Iterable<JsonObject>> accountsMatcher) {

    return new LoanAccountMatcher("Lost item fee (actual cost)", accountsMatcher);
  }

  public static LoanAccountMatcher hasLostItemProcessingFee(
    Matcher<JsonObject> accountMatcher) {

    return hasLostItemProcessingFees(hasItems(accountMatcher));
  }

  public static LoanAccountMatcher hasNoLostItemProcessingFee() {
    return hasLostItemProcessingFees(iterableWithSize(0));
  }

  public static LoanAccountMatcher hasLostItemProcessingFees(
    Matcher<Iterable<JsonObject>> accountMatcher) {

    return new LoanAccountMatcher("Lost item processing fee", accountMatcher);
  }

  public static LoanAccountMatcher hasOverdueFine() {
    return new LoanAccountMatcher("Overdue fine",
      hasItems(notNullValue(JsonObject.class)));
  }

  public static LoanAccountMatcher hasNoOverdueFine() {
    return new LoanAccountMatcher("Overdue fine", iterableWithSize(0));
  }
}