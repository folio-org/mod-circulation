package api.requests.scenarios;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.LoanHistoryMatcher.hasLoanHistoryRecord;
import static api.support.utl.BlockOverridesUtils.OVERRIDE_RENEWAL_PERMISSION;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.OkapiHeaders;
import lombok.val;

public class RecallItemsTests extends APITests {
  public RecallItemsTests() {
    super(true,true);
  }

  @Test
  void loanActionCommentIsRemovedOnRecall() {
    // using non renewable loan policy just to be able to specify action comment
    // on override renew
    use(new LoanPolicyBuilder().withName("loanActionCommentIsRemovedOnRecall")
      .rolling(Period.weeks(3)).notRenewable().renewFromSystemDate());

    val overrideRenewComment = "Override renew";
    val newDueDate = DateTime.now(DateTimeZone.UTC).plusMonths(3).toString();

    val item = itemsFixture.basedUponNod();
    val user = usersFixture.james();
    val loan = checkOutFixture.checkOutByBarcode(item, user);

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(OVERRIDE_RENEWAL_PERMISSION);
    loansFixture.overrideRenewalByBarcode(item, user, overrideRenewComment, newDueDate, okapiHeaders);

    requestsFixture.recallItem(item, usersFixture.charlotte());

    assertThat(loan, hasLoanHistoryRecord(allOf(
      hasJsonPath("loan.action", "recallrequested"),
      hasNoJsonPath("loan.actionComment")
    )));
  }
}
