package api.support.matchers;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.ResourceClient.forAccounts;
import static api.support.http.ResourceClient.forFeeFineActions;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.allOf;

import java.util.UUID;
import java.util.function.Function;

import api.support.http.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import api.support.MultipleJsonRecords;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

class LoanAccountActionsMatcher extends TypeSafeDiagnosingMatcher<IndividualResource> {
  private static final String LOST_ITEM_FEE = "Lost item fee";
  private static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";

  private final ResourceClient accountsClient;
  private final ResourceClient accountActionsClient;
  private final String feeFineType;
  private final Function<JsonObject, Matcher<Iterable<JsonObject>>> matcherFactory;

  public LoanAccountActionsMatcher(String feeFineType, Matcher<Iterable<JsonObject>> matcher) {
    this(feeFineType, json -> matcher);
  }

  private LoanAccountActionsMatcher(String feeFineType,
    Function<JsonObject, Matcher<Iterable<JsonObject>>> matcherFactory) {

    this.feeFineType = feeFineType;
    this.matcherFactory = matcherFactory;
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

    final Matcher<Iterable<JsonObject>> actionsMatcher = matcherFactory.apply(accountForLoan);
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
      .appendText(" with actions matching criteria");
  }

  public static LoanAccountActionsMatcher hasLostItemFeeCreatedBySystemAction() {
    return new LoanAccountActionsMatcher(LOST_ITEM_FEE,
      account -> hasItems(allOf(
        hasNoJsonPath("createdAt"),
        hasJsonPath("source", "System"),
        hasJsonPath("amountAction", account.getDouble("amount")),
        hasJsonPath("balance", account.getDouble("amount")),
        hasJsonPath("typeAction", account.getString("feeFineType")))));
  }

  public static LoanAccountActionsMatcher hasLostItemProcessingFeeCreatedBySystemAction() {
    return new LoanAccountActionsMatcher(LOST_ITEM_PROCESSING_FEE,
      account -> hasItems(allOf(
        hasNoJsonPath("createdAt"),
        hasJsonPath("source", "System"),
        hasJsonPath("amountAction", account.getDouble("amount")),
        hasJsonPath("balance", account.getDouble("amount")),
        hasJsonPath("typeAction", account.getString("feeFineType")))));
  }

  public static LoanAccountActionsMatcher hasLostItemFeeActions(
    Matcher<Iterable<JsonObject>> actionsMatcher) {

    return new LoanAccountActionsMatcher(LOST_ITEM_FEE, actionsMatcher);
  }

  public static Matcher<IndividualResource> hasLostItemProcessingFeeActions(
    Matcher<Iterable<JsonObject>> actionsMatcher) {

    return new LoanAccountActionsMatcher(LOST_ITEM_PROCESSING_FEE, actionsMatcher);
  }
}
