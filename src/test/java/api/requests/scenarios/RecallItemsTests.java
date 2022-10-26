package api.requests.scenarios;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.LoanHistoryMatcher.hasLoanHistoryRecord;
import static api.support.utl.BlockOverridesUtils.OVERRIDE_RENEWAL_PERMISSION;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import api.support.http.OkapiHeaders;
import lombok.val;

class RecallItemsTests extends APITests {
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
    val newDueDate = formatDateTime(ClockUtil.getZonedDateTime().plusMonths(3));

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

  @Test
  void whenPolicyHasTwoDifferentFixedSchedulesRecallShouldApplyToTheScheduleForTheDueDateAfterRenew() {
    ZoneId londonZoneId = ZoneId.of("Europe/London");

    ZonedDateTime fromFirst = ZonedDateTime.of(2022, 1, 2, 0, 0, 0, 0, londonZoneId);
    ZonedDateTime toFirst = ZonedDateTime.of(2022, 1, 4, 23, 59, 59, 0, londonZoneId);
    ZonedDateTime dueFirst = ZonedDateTime.of(2022, 1, 9, 23, 59, 59, 0, londonZoneId);

    ZonedDateTime fromSecond = ZonedDateTime.of(2022, 1, 17, 0, 0, 0, 0, londonZoneId);
    ZonedDateTime toSecond = ZonedDateTime.of(2022, 1, 20, 23, 59, 59, 0, londonZoneId);
    ZonedDateTime dueSecond = ZonedDateTime.of(2022, 1, 27, 23, 59, 59, 0, londonZoneId);

    FixedDueDateSchedulesBuilder builder = new FixedDueDateSchedulesBuilder()
      .addSchedule(new FixedDueDateSchedule(fromFirst, toFirst, dueFirst))
      .addSchedule(new FixedDueDateSchedule(fromSecond, toSecond, dueSecond));
    IndividualResource fixedDueDateSchedules = fixedDueDateScheduleClient.create(builder);
    Period recallInterval = Period.days(2);
    use(new LoanPolicyBuilder().fixed(fixedDueDateSchedules.getId())
      .limitedRenewals(1)
      .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
      .withRecallsRecallReturnInterval(recallInterval)
      .withRenewable(true));

    val item = itemsFixture.basedUponNod();
    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    ZonedDateTime loanDate = fromFirst.plusDays(2);
    val loan = checkOutFixture.checkOutByBarcode(item, james, loanDate);
    assertThat(loan.getJson().getInstant("dueDate").atZone(londonZoneId),
      is(dueFirst));

    ClockUtil.setClock(Clock.fixed(toSecond.minusHours(3).toInstant(), londonZoneId));

    assertThat(loansFixture.renewLoan(item, james).getJson()
      .getInstant("dueDate").atZone(londonZoneId), is(dueSecond));

    requestsFixture.recallItem(item, jessica);

    assertThat(loansFixture.getLoanById(loan.getId()).getJson().getInstant("dueDate")
      .atZone(londonZoneId), is(recallInterval.plusDate(ClockUtil.getZonedDateTime())));
  }
}
