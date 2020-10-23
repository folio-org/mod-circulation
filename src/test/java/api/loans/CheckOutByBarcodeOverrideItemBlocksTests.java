package api.loans;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeOverrideItemBlocksRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeOverrideItemBlocksTests extends APITests {
  private static final UUID CHECK_OUT_SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID LOAN_POLICY_ID = UUID.randomUUID();
  private static final String LOAN_POLICY_NAME = "One day with item limit";
  private static final String TEST_COMMENT = "Test comment";
  private static final String CHECKED_OUT_THROUGH_OVERRIDE = "checkedOutThroughOverride";

  @Test
  public void canOverrideItemBlocksWhenItemLimitIsReached() {
    UserResource steve = usersFixture.steve();
    ItemResource firstItem = itemsFixture.basedUponNod();
    ItemResource secondItem = itemsFixture.basedUponSmallAngryPlanet();

    prepareCirculationRules(1);

    checkOutFixture.checkOutByBarcode(firstItem, steve);
    assertThat(itemsClient.get(firstItem), hasItemStatus(CHECKED_OUT));

    IndividualResource overrideItemBlocksResponse =
      checkOutFixture.checkOutByBarcodeOverrideItemBlocks(
        buildOverrideRequest(steve, secondItem, TEST_COMMENT));

    JsonObject responseJson = overrideItemBlocksResponse.getJson();

    assertThat(itemsClient.get(secondItem), hasItemStatus(CHECKED_OUT));
    assertThat(responseJson.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
    assertThat(responseJson.getString("actionComment"), is(TEST_COMMENT));
  }

  @Test
  public void canNotOverrideItemBlocksWhenItemLimitIsNotReached() {
    IndividualResource steve = usersFixture.steve();
    ItemResource item = itemsFixture.basedUponNod();

    prepareCirculationRules(1);

    IndividualResource overrideItemBlocksResponse =
      checkOutFixture.attemptCheckOutByBarcodeOverrideItemBlocks(422,
        buildOverrideRequest(steve, item, TEST_COMMENT));

    assertThat(overrideItemBlocksResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has not reached maximum limit of 1 items for material type"),
      hasParameter("itemBarcode", item.getBarcode()))));

    assertThat(itemsClient.get(item), hasItemStatus(AVAILABLE));
  }

  @Test
  public void canNotOverrideItemBlocksWhenItemLimitIsNotSet() {
    IndividualResource steve = usersFixture.steve();
    ItemResource item = itemsFixture.basedUponNod();

    prepareCirculationRules(null);

    IndividualResource overrideItemBlocksResponse =
      checkOutFixture.attemptCheckOutByBarcodeOverrideItemBlocks(422,
        buildOverrideRequest(steve, item, TEST_COMMENT));

    assertThat(overrideItemBlocksResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item limit is not set in Loan Policy"),
      hasParameter("loanPolicyName", LOAN_POLICY_NAME),
      hasParameter("loanPolicyId", LOAN_POLICY_ID.toString()),
      hasParameter("itemLimit", null)
    )));

    assertThat(itemsClient.get(item), hasItemStatus(AVAILABLE));
  }

  @Test
  public void canNotOverrideItemBlocksWhenCommentIsMissing() {
    IndividualResource steve = usersFixture.steve();
    ItemResource item = itemsFixture.basedUponNod();

    prepareCirculationRules(1);

    IndividualResource overrideItemBlocksResponse =
      checkOutFixture.attemptCheckOutByBarcodeOverrideItemBlocks(422,
        buildOverrideRequest(steve, item, null));

    assertThat(overrideItemBlocksResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override request must contain a non-blank comment"),
      hasParameter("comment", null))));

    assertThat(itemsClient.get(item), hasItemStatus(AVAILABLE));
  }

  @Test
  public void canNotOverrideItemBlocksWhenCommentIsBlank() {
    IndividualResource steve = usersFixture.steve();
    ItemResource item = itemsFixture.basedUponNod();

    prepareCirculationRules(1);

    String comment = " ";

    IndividualResource overrideItemBlocksResponse =
      checkOutFixture.attemptCheckOutByBarcodeOverrideItemBlocks(422,
        buildOverrideRequest(steve, item, comment));

    assertThat(overrideItemBlocksResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override request must contain a non-blank comment"),
      hasParameter("comment", comment))));

    assertThat(itemsClient.get(item), hasItemStatus(AVAILABLE));
  }

  private static CheckOutByBarcodeOverrideItemBlocksRequestBuilder buildOverrideRequest(
    IndividualResource patron, ItemResource item, String comment) {

    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder()
      .forItem(item)
      .to(patron)
      .at(CHECK_OUT_SERVICE_POINT_ID)
      .withComment(comment);
  }

  private void prepareCirculationRules(Integer itemLimit) {
    IndividualResource loanPolicyWithItemLimit = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withId(LOAN_POLICY_ID)
        .withName(LOAN_POLICY_NAME)
        .withDescription("Loan policy with item limit")
        .withItemLimit(itemLimit)
        .rolling(Period.days(1))
        .unlimitedRenewals()
        .renewFromSystemDate()
    );

    IndividualResource fallbackLoanPolicyWithoutItemLimit = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withId(UUID.randomUUID())
        .withName("One day without item limit")
        .withDescription("Loan policy without item limit")
        .withItemLimit(null)
        .rolling(Period.days(1))
        .unlimitedRenewals()
        .renewFromSystemDate()
    );

    final String loanPolicyWithoutItemLimitId = fallbackLoanPolicyWithoutItemLimit.getId().toString();
    final String loanPolicyWithItemLimitId = loanPolicyWithItemLimit.getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    final String materialTypeBook = materialTypesFixture.book().getId().toString();

    circulationRulesFixture.updateCirculationRules(String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + loanPolicyWithoutItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy,
      "m " + materialTypeBook + ": l " + loanPolicyWithItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy));
  }

}
