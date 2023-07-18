package api.requests.scenarios;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestPolicyTests extends APITests {

  private final IndividualResource requestPickupServicePoint;

  public RequestPolicyTests() {

    requestPickupServicePoint = servicePointsFixture.cd1();
  }

  @Test
  void canCreateRecallRequestsWithRequestPolicyAllowingRecalls() {

    final String undergradPatronGroup = patronGroupsFixture.undergrad().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String recallRequestPolicy = requestPoliciesFixture.recallRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the patronGroup policy g is used instead.
    //The patronGroup policy allows undergraduate students to place a request on any material type, loan or notice type.
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy + "\n",
      "g " + undergradPatronGroup + ": l " + anyLoanPolicy + " r " + recallRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    //setting up a checked-out library item to perform recall
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    //an undergraduate student attempts to place a Recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.undergradHenry()));

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void cannotCreateRecallRequestsWithRequestPolicyNotAllowingRecalls() {

    final String undergradPatronGroupPolicy = patronGroupsFixture.undergrad().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.HOLD);
    allowedRequestTypes.add(RequestType.PAGE);
    final String noRecallRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
                                          "All But Recall", "All but Recall request policy").getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the patronGroup policy g is used instead.
    //The patronGroup policy allows undergraduate students to place a request on any material type, loan or notice type, on any request type
    //except for RECALL
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy +  "\n",
      "g " + undergradPatronGroupPolicy + ": l " + anyLoanPolicy + " r " + noRecallRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    //setting up a checked-out library item to perform recall
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.undergradHenry()));

    assertThat(recallResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Recall requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Recall"))));
  }

  @Test
  void canCreateHoldRequestsWithRequestWithReqestPolicyAllowingHolds() {

    final String holdRequestPolicy = requestPoliciesFixture.holdRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String bookMaterialType = materialTypesFixture.book().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the material policy m is used instead.
    //The materialType policy allows any patron to place a request on a library item of BOOK material type, any loan and notice types.
    final String rule = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy +  "\n",
      "m " + bookMaterialType + ": l " + anyLoanPolicy + " r " + holdRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rule);

    //setting up a checked-out library item of material type BOOK to perform a HOLD
    final IndividualResource checkedOutItem = itemsFixture.basedUponUprooted();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    //an undergraduate student attempts to place a HOLD request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.steve()));

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void cannotCreateHoldRequestsWithRequestPolicyNotAllowingHolds() {

    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String bookMaterialType = materialTypesFixture.book().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.RECALL);
    allowedRequestTypes.add(RequestType.PAGE);
    final String noHoldRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
      "All But Hold", "All but Hold request policy").getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the material type rule m is used instead.
    //The material type rule m allows any patron to place any request but HOLDs on any BOOK, loan or notice types
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy +  "\n",
      "m " + bookMaterialType + ": l " + anyLoanPolicy + " r " + noHoldRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    //setting up a checked-out library item to perform HOLD
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    final Response holdResponse = requestsClient.attemptCreate(new RequestBuilder()
      .hold()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));  //randomly picked James to represent "Any" patron

    assertThat(holdResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Hold requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Hold"))));
  }

  @Test
  void canCreatePageRequestsWithRequestPolicyAllowingPageRequests() {

    final String nonCirculatingLoanTypePolicy = loanPoliciesFixture.canCirculateFixed().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String pageRequestPolicy = requestPoliciesFixture.pageRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + nonCirculatingLoanTypePolicy + " r " + pageRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    //setting up an available library item to perform Page request
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();

    //a patron attempts to place a request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.steve()));

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.PAGE.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void cannotCreatePageRequestsWithRequestPolicyNotAllowingPagings() {

    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String nonCirculatingLoanTypePolicy = loanPoliciesFixture.canCirculateFixed().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.RECALL);
    allowedRequestTypes.add(RequestType.HOLD);
    final String noPageRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
      "All But Page", "All but Page request policy").getId().toString();

    final String rules =
      "priority: t, s, c, b, a, m, g " +
      "fallback-policy : l " + nonCirculatingLoanTypePolicy + " r " + noPageRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy;

    setRules(rules);

    //setting up a checked-out library item to attempt placing a PAGE request
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();

    final Response pageResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));  //randomly picked James to represent "Any" patron

    assertThat(pageResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Page requests are not allowed for this patron and item combination"),
      hasParameter("requestType", "Page"))));
  }

  @Test
  void canCreateRecallRequestsWithRequestPolicyUsingFallbackRules() {

    final String undergradPatronGroup = patronGroupsFixture.undergrad().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String recallRequestPolicy = requestPoliciesFixture.recallRequestPolicy().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    //This rule is set up to show that the fallback policy will be used instead of the undergrad patronGroup policy.
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy + "\n",
      "g " + undergradPatronGroup + ": l " + anyLoanPolicy + " r " + recallRequestPolicy +" n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy
    );

    setRules(rules);

    //setting up a checked-out library item to perform recall
    final IndividualResource checkedOutItem = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());

    //an undergraduate student attempts to place a Recall request
    final IndividualResource recallRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));  //james does not belong to the Undergrad patron group

    JsonObject requestedItem = recallRequest.getJson().getJsonObject("item");
    assertThat(recallRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));
    assertThat(requestedItem.getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(recallRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
  }

  @Test
  void cannotCreatePageRequestsWithoutCirculationRulesDefined() {

    //In order to not have any request policy around, need to remove the default one which was created before each test run
    final String defaultRequestPolicyName = "Example Request Policy";
    IndividualResource defaultRequestPolicy = requestPoliciesFixture.findRequestPolicy(defaultRequestPolicyName);
    requestPoliciesFixture.deleteRequestPolicy(defaultRequestPolicy);

    //setting up a checked-out library item to perform recall
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));  //randomly picked James to represent "Any" patron

    String expectedErrorMessage = "Request policy " + defaultRequestPolicy.getId() + " could not be found, please check circulation rules";

    assertThat(recallResponse, hasStatus(HTTP_INTERNAL_SERVER_ERROR));
    assertTrue(recallResponse.getBody().equalsIgnoreCase(expectedErrorMessage));
  }

  @Test
  void cannotCreatePageRequestsWithoutMatchingCirculationRules() {

    //In order to not have any request policy around, need to remove the default one which was created before each test run
    final String defaultRequestPolicyName = "Example Request Policy";
    IndividualResource defaultRequestPolicy = requestPoliciesFixture.findRequestPolicy(defaultRequestPolicyName);
    requestPoliciesFixture.deleteRequestPolicy(defaultRequestPolicy);

    //setting up a checked-out library item to perform recall
    final IndividualResource availableItem = itemsFixture.basedUponSmallAngryPlanet();

    final Response recallResponse = requestsClient.attemptCreate(new RequestBuilder()
      .page()
      .forItem(availableItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));  //randomly picked James to represent "Any" patron

    String expectedErrorMessage = "Request policy " + defaultRequestPolicy.getId() + " could not be found, please check circulation rules";

    assertThat(recallResponse, hasStatus(HTTP_INTERNAL_SERVER_ERROR));
    assertTrue(recallResponse.getBody().equalsIgnoreCase(expectedErrorMessage));
  }

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void createdRequestCanUseRequestPolicyWithAllowedServicePoints(RequestType requestType) {
    final UUID requestPolicyId = setRequestPolicyWithAllowedServicePoints(requestType);
    final IndividualResource item = itemsFixture.basedUponUprooted();
    createRequest(requestType, item);

    IndividualResource requestPolicyById = requestPolicyClient.get(requestPolicyId);

    assertThat(requestPolicyById.getId(), is(requestPolicyId));
    JsonArray allowedIds = requestPolicyById.getJson().getJsonObject("allowedServicePoints")
      .getJsonArray(requestType.getValue());
    assertThat(allowedIds.size(), is(1));
    assertThat(allowedIds.getString(0), is(requestPickupServicePoint.getId().toString()));
  }

  private UUID setRequestPolicyWithAllowedServicePoints(RequestType requestType) {
    final Map<RequestType, Set<UUID>> allowedServicePoints = new HashMap<>();
    allowedServicePoints.put(requestType, Set.of(requestPickupServicePoint.getId()));
    var requestPolicy = requestPoliciesFixture
      .createRequestPolicyWithAllowedServicePoints(allowedServicePoints, requestType);
    policiesActivation.use(PoliciesToActivate.builder().requestPolicy(requestPolicy));

    return requestPolicy.getId();
  }

  private void createRequest(RequestType requestType, IndividualResource checkedOutItem) {
    if (requestType != RequestType.PAGE) {
      checkOutFixture.checkOutByBarcode(checkedOutItem, usersFixture.jessica());
    }
    requestsClient.create(new RequestBuilder()
      .withRequestType(requestType.getValue())
      .forItem(checkedOutItem)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.steve()));
  }

  private void setRules(String rules) {
    try {
      circulationRulesFixture.updateCirculationRules(rules);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
