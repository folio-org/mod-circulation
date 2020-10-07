package api.loans;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsCountIsEqualTo;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLogRecordEventsAreValid;
import static api.support.builders.FixedDueDateSchedule.forDay;
import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Seconds.seconds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class OverrideRenewByBarcodeTests extends APITests {
  private static final String OVERRIDE_COMMENT = "Comment to override";
  private static final String ITEM_IS_NOT_LOANABLE_MESSAGE = "item is not loanable";
  private static final String ACTION_COMMENT_KEY = "actionComment";
  private static final String RENEWED_THROUGH_OVERRIDE = "renewedThroughOverride";

  @Test
  public void cannotOverrideRenewalWhenLoanPolicyDoesNotExist() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID unknownLoanPolicyId = UUID.randomUUID();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, APRIL, 21, 11, 21, 43));

    IndividualResource record = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(unknownLoanPolicyId)
      .withName("Example loanPolicy"));
    useFallbackPolicies(
      unknownLoanPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId()
    );
    loanPoliciesFixture.delete(record);

    final Response response = loansFixture.attemptRenewal(500, smallAngryPlanet,
      jessica);

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check circulation rules", unknownLoanPolicyId)));
  }

  @Test
  public void cannotOverrideRenewalWhenLoaneeCannotBeFound() {
    val smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    usersFixture.remove(steve);

    final Response response = loansFixture.attemptOverride(smallAngryPlanet,
      steve, OVERRIDE_COMMENT, null);

    //Occurs when current loanee is not found, so relates to loan rather than user in request
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("user is not found"),
      hasParameter("userId", steve.getId().toString()))));
  }

  @Test
  public void cannotOverrideRenewalWhenItemCannotBeFound() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    itemsClient.delete(smallAngryPlanet.getId());

    final Response response = loansFixture.attemptOverride(smallAngryPlanet,
      steve, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasItemNotFoundMessage(smallAngryPlanet),
      hasItemRelatedParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotOverrideRenewalLoanForDifferentUser() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, APRIL, 21, 11, 21, 43));

    final Response response = loansFixture.attemptOverride(smallAngryPlanet,
      james, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot renew item checked out to different user"),
      hasUserRelatedParameter(james))));
  }

  @Test
  public void cannotOverrideRenewalWhenCommentPropertyIsBlank() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    final Response response = loansFixture.attemptOverride(smallAngryPlanet,
      jessica, EMPTY, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal request must have a comment"),
      hasParameter("comment", null))));
  }

  @Test
  public void canOverrideRenewalWhenItemIsNotRenewableAndNewDueDateIsNotSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    use(nonRenewablePolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    Response response = loansFixture.attemptOverride(smallAngryPlanet, jessica,
      OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"))));
  }

  @Test
  public void canOverrideRenewalWhenItemIsNotRenewableAndNewDueDateIsSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    final IndividualResource loanPolicy = loanPoliciesFixture.create(nonRenewablePolicy);
    UUID nonRenewablePolicyId = loanPolicy.getId();

    use(nonRenewablePolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    DateTime newDueDate = DateTime.now().plusWeeks(2);

    JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString()).getJson();

    verifyRenewedLoan(smallAngryPlanet, jessica, renewedLoan);

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    //TODO loanpolicyname is not stored, possible bug?
    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(nonRenewablePolicyId.toString()));

    assertThat("due date should be 2 weeks from now",
      renewedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheFixedLoanPolicyAndDueDateIsNotSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    use(currentDueDateRollingPolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    Response response = loansFixture.attemptOverride(smallAngryPlanet, jessica,
      OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"))));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheFixedLoanPolicyAndDueDateIsSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, loanDueDate);

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY));

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    final IndividualResource loanPolicy = loanPoliciesFixture.create(currentDueDateRollingPolicy);
    UUID dueDateLimitedPolicyId = loanPolicy.getId();

    use(currentDueDateRollingPolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    DateTime newDueDate = DateTime.now().plusWeeks(1);
    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString()).getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    verifyRenewedLoan(smallAngryPlanet, jessica, renewedLoan);

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    //TODO loanpolicyname is not stored, possible bug?
    assertThat("last loan policy should be stored",
            renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months from previous due date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(ItemBuilder.CHECKED_OUT));
  }

  @Test
  public void canOverrideRenewalWhenDateFallsOutsideOfTheDateRangesInTheRollingLoanPolicy() {
    final DateTime renewalDate = DateTime.now();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate =
      new DateTime(2018, APRIL, 21, 11, 21, 43);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, jessica, loanDueDate);

    final UUID loanId = loan.getId();

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2018, DateTimeConstants.FEBRUARY))
      .addSchedule(forDay(renewalDate));

    final UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .limitedBySchedule(fixedDueDateSchedulesId)
      .renewFromCurrentDueDate();

    use(currentDueDateRollingPolicy);

    Response response = loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("renewal date falls outside of date ranges in the loan policy"))));

    final DateTime newDueDate = loanDueDate.plusWeeks(3).plusMonths(2);

    final JsonObject renewedLoan = loansFixture.overrideRenewalByBarcode(
      smallAngryPlanet, jessica, OVERRIDE_COMMENT, newDueDate.toString())
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    verifyRenewedLoan(smallAngryPlanet, jessica, renewedLoan);

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("due date should be 1st of Feb 2019",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));
  }

  @Test
  public void canOverrideRenewalWhenLoanReachedRenewalLimitAndDueDateIsNotSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals policy")
      .rolling(Period.weeks(1))
      .limitedRenewals(1);

    use(limitedRenewalsPolicy);

    DateTime loanDate = DateTime.now(UTC);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDate).getJson();

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, null)
        .getJson();

    verifyRenewedLoan(smallAngryPlanet, jessica, renewedLoan);

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(2));

    DateTime expectedDueDate = loanDate.plusWeeks(3);
    assertThat("due date should be 3 weeks later",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(expectedDueDate));
  }

  @Test
  public void canOverrideRenewalWhenItemIsDeclaredLost() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals policy")
      .rolling(Period.weeks(1));

    use(defaultRollingPolicies()
      .loanPolicy(loanPoliciesFixture.create(limitedRenewalsPolicy))
      // Have to charge a fine otherwise the loan is closed when item is declared lost
      .lostItemPolicy(lostItemFeePoliciesFixture.chargeFee()));

    final DateTime loanDate = DateTime.now(UTC).minusWeeks(1);

    final JsonObject loanJson = checkOutFixture.checkOutByBarcode(smallAngryPlanet,
      usersFixture.jessica(), loanDate).getJson();

    declareLostFixtures.declareItemLost(loanJson);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    final JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, null).getJson();

    verifyRenewedLoan(smallAngryPlanet, jessica, renewedLoan);

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("item status should be changed",
      renewedLoan.getJsonObject("item").getJsonObject("status").getString("name"),
      is(CHECKED_OUT.getValue()));

    DateTime expectedDueDate = loanDate.plusWeeks(2);
    assertThat("due date should be 2 weeks later",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(expectedDueDate));
  }

  @Test
  public void canOverrideRenewalWhenItemIsAgedToLost() {
    final DateTime approximateRenewalDate = DateTime.now(UTC).plusWeeks(3);
    val result = ageToLostFixture.createAgedToLostLoan();

    final JsonObject renewedLoan = loansFixture
      .overrideRenewalByBarcode(result.getItem(), result.getUser(), OVERRIDE_COMMENT, null)
      .getJson();

    verifyRenewedLoan(result.getItem(), result.getUser(), renewedLoan);

    assertThat(renewedLoan, hasJsonPath("item.status.name", "Checked out"));
    assertThat(itemsClient.get(result.getItem()).getJson(), isCheckedOut());
    assertThat(renewedLoan.getString("dueDate"),
      withinSecondsAfter(seconds(2), approximateRenewalDate));
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void cannotOverrideRenewalWhenLoanDoesNotMatchAnyOfOverrideCases() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate();

    use(currentDueDateRollingPolicy);

    final Response response = loansFixture.attemptOverride(smallAngryPlanet,
      jessica, OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not loanable, " +
        "item is not renewable, " +
        "reached number of renewals limit," +
        "renewal date falls outside of the date ranges in the loan policy, " +
        "items cannot be renewed when there is an active recall request, " +
        "item is Declared lost, item is Claimed returned, item is Aged to lost, " +
        "renewal would not change the due date"))));
  }

  @Test
  public void renewalRemovesActionCommentAfterOverride() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();
    createLoanPolicyAndSetAsFallback(nonRenewablePolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    DateTime newDueDate = DateTime.now().plusWeeks(2);

    JsonObject loanAfterOverride =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString()).getJson();
    assertLoanHasActionComment(loanAfterOverride, OVERRIDE_COMMENT);

    LoanPolicyBuilder renewablePolicy = new LoanPolicyBuilder()
      .withName("Renewable Policy")
      .rolling(Period.days(2));
    createLoanPolicyAndSetAsFallback(renewablePolicy);

    JsonObject loanAfterRenewal = loansFixture.renewLoan(smallAngryPlanet, jessica).getJson();
    assertActionCommentIsAbsentInLoan(loanAfterRenewal);
  }

  @Test
  public void checkInRemovesActionCommentAfterOverride() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder nonRenewablePolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();
    createLoanPolicyAndSetAsFallback(nonRenewablePolicy);

    loansFixture.attemptRenewal(422, smallAngryPlanet, jessica);

    DateTime newDueDate = DateTime.now().plusWeeks(2);

    JsonObject loanAfterOverride =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString()).getJson();
    assertLoanHasActionComment(loanAfterOverride, OVERRIDE_COMMENT);

    JsonObject loanAfterCheckIn = checkInFixture.checkInByBarcode(smallAngryPlanet).getLoan();
    assertActionCommentIsAbsentInLoan(loanAfterCheckIn);
  }

  @Test
  public void cannotOverrideRenewalWhenItemIsNotLoanableAndNewDueDateIsNotSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder notLoanablePolicy = new LoanPolicyBuilder()
      .withName("Not Loanable Policy")
      .withLoanable(false);
    createLoanPolicyAndSetAsFallback(notLoanablePolicy);

    JsonObject renewalResponse = loansFixture.attemptRenewal(422,
      smallAngryPlanet, jessica).getJson();

    assertThat(renewalResponse, hasErrorWith(allOf(
      hasMessage(ITEM_IS_NOT_LOANABLE_MESSAGE))));

    Response response = loansFixture.attemptOverride(smallAngryPlanet, jessica,
      OVERRIDE_COMMENT, null);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("New due date must be specified when due date calculation fails"))));
  }

  @Test
  public void canOverrideRenewalWhenItemIsNotLoanableAndNewDueDateIsSpecified() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    DateTime loanDueDate = new DateTime(2018, APRIL, 21, 11, 21, 43);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, loanDueDate);

    LoanPolicyBuilder notLoanablePolicy = new LoanPolicyBuilder()
      .withName("Not Loanable Policy")
      .withLoanable(false);
    createLoanPolicyAndSetAsFallback(notLoanablePolicy);

    JsonObject renewalResponse =
      loansFixture.attemptRenewal(422, smallAngryPlanet, jessica).getJson();
    assertThat(renewalResponse, hasErrorWith(allOf(
      hasMessage(ITEM_IS_NOT_LOANABLE_MESSAGE))));

    DateTime newDueDate = DateTime.now().plusWeeks(2);

    JsonObject renewedLoan =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString()).getJson();

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is(RENEWED_THROUGH_OVERRIDE));
    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString(ACTION_COMMENT_KEY), is(OVERRIDE_COMMENT));
    assertThat("due date should be 2 weeks from now",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(newDueDate));
  }


  @Test
  public void cannotOverrideRenewalWhenDueDateIsEarlierOrSameAsCurrentLoanDueDate() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica, DateTime.now());

    LoanPolicyBuilder loanablePolicy = new LoanPolicyBuilder()
      .withName("Loanable Policy")
      .withLoanable(true)
      .rolling(Period.days(1))
      .limitedRenewals(0)
      .renewFromSystemDate();
    createLoanPolicyAndSetAsFallback(loanablePolicy);

    DateTime newDueDate = DateTime.now().plusDays(3);

    Response response = loansFixture.attemptOverride(smallAngryPlanet, jessica,
        OVERRIDE_COMMENT, newDueDate.toString());

    assertThat(response.getJson(), hasErrorWith(hasRenewalWouldNotChangeDueDateMessage()));
  }

  @Test
  public void renewalNoticeIsSentWhenPolicyDefinesRenewalNoticeConfiguration() {
    UUID renewalTemplateId = UUID.randomUUID();
    JsonObject renewalNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(renewalTemplateId)
      .withRenewalEvent()
      .create();
    JsonObject checkInNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder renewalNoticePolicy = new NoticePolicyBuilder()
      .withName("Policy with renewal notice")
      .withLoanNotices(Arrays.asList(renewalNoticeConfiguration, checkInNoticeConfiguration));

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    use(loanPolicy, renewalNoticePolicy);

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId(),
      EMPTY,
      "ItemPrefix",
      "ItemSuffix",
      "");

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, itemsFixture.thirdFloorHoldings());
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    IndividualResource loanAfterRenewal =
      loansFixture.overrideRenewalByBarcode(smallAngryPlanet, steve,
        OVERRIDE_COMMENT, loanDate.plusDays(4).toString());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    int expectedRenewalLimit = 0;
    int expectedRenewalsRemaining = 0;
    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(steve));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(smallAngryPlanet, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanContextMatchers(loanAfterRenewal));
    noticeContextMatchers.putAll(TemplateContextMatchers.getLoanPolicyContextMatchers(
      expectedRenewalLimit, expectedRenewalsRemaining));

    assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(steve.getId(), renewalTemplateId, noticeContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
    assertThatPublishedLogRecordEventsAreValid();
  }

  private Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user) {
    return hasParameter("userBarcode", user.getJson().getString("barcode"));
  }

  private Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item) {
    return hasParameter("itemBarcode", item.getJson().getString("barcode"));
  }

  private Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item) {
    return hasMessage(String.format("No item with barcode %s exists",
      item.getJson().getString("barcode")));
  }

  private Matcher<ValidationError> hasRenewalWouldNotChangeDueDateMessage() {
    return hasMessage("renewal would not change the due date");
  }

  private void createLoanPolicyAndSetAsFallback(LoanPolicyBuilder loanPolicyBuilder) {
    use(loanPolicyBuilder);
  }

  private void assertLoanHasActionComment(JsonObject loan, String actionComment) {
    assertThat("loan should have 'actionComment' property",
      loan.getString(ACTION_COMMENT_KEY), is(actionComment));
  }
  private void assertActionCommentIsAbsentInLoan(JsonObject loan) {
    assertThat("'actionComment' property should be absent in loan",
      loan.getString(ACTION_COMMENT_KEY), nullValue());
  }

  private void verifyRenewedLoan(IndividualResource smallAngryPlanet,
    IndividualResource jessica, JsonObject renewedLoan) {
    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is(RENEWED_THROUGH_OVERRIDE));

    assertThat("'actionComment' field should contain comment specified for override",
      renewedLoan.getString(ACTION_COMMENT_KEY), is(OVERRIDE_COMMENT));
  }
}
