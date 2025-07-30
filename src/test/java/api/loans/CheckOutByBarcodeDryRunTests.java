package api.loans;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasProxyUserBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.domain.representations.CheckOutByBarcodeDryRunRequest;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutBlockOverrides;
import api.support.builders.CheckOutByBarcodeDryRunRequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.CheckOutResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

class CheckOutByBarcodeDryRunTests extends APITests {

  public static final String OVERRIDE_PATRON_BLOCK_PERMISSION =
    "circulation.override-patron-block.post";

  @Test
  void shouldRetrievePolicyIds() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var steve = usersFixture.steve();
    var response = checkOutFixture.checkOutByBarcodeDryRun(new CheckOutByBarcodeDryRunRequest(
      smallAngryPlanet.getBarcode(), steve.getBarcode(), null, null));
    var responseJson = response.getJson();

    assertThat(responseJson.getString("loanPolicyId"), is(loanPoliciesFixture.canCirculateRolling().getId()));
    assertThat(responseJson.getString("overdueFinePolicyId"), is(overdueFinePoliciesFixture.facultyStandard().getId()));
    assertThat(responseJson.getString("lostItemPolicyId"), is(lostItemFeePoliciesFixture.facultyStandard().getId()));
    assertThat(responseJson.getString("patronNoticePolicyId"), is(noticePoliciesFixture.activeNotice().getId()));
  }

  @Test
  void shouldReturnErrorIfPatronIsInactive() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var steve = usersFixture.steve(UserBuilder::inactive);

    var response = checkOutFixture.attemptCheckOutByBarcodeDryRun(
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null, null));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out to inactive user"),
      hasUserBarcodeParameter(steve))));
  }

  @Test
  void shouldReturnErrorIfProxyIsInactive() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var james = usersFixture.james();
    var steve = usersFixture.steve(UserBuilder::inactive);

    proxyRelationshipsFixture.currentProxyFor(james, steve);

    var response = checkOutFixture.attemptCheckOutByBarcodeDryRun(
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(),
        james.getBarcode(), steve.getBarcode(), null));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out via inactive proxying user"),
      hasProxyUserBarcodeParameter(steve))));
  }

  @Test
  void shouldReturnErrorIfItemNotFound() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var steve = usersFixture.steve();
    itemsClient.delete(smallAngryPlanet.getId());

    var response = checkOutFixture.attemptCheckOutByBarcodeDryRun(
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null, null));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No item with barcode 036000291452 could be found"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  void shouldReturnErrorIfItemAlreadyCheckedOut() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var jessica = usersFixture.jessica();
    var steve = usersFixture.steve();
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    var response = checkOutFixture.attemptCheckOutByBarcodeDryRun(
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null, null));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  void canOverrideCheckOutDryRunWhenAutomatedPatronBlockIsPresent() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UserResource user = usersFixture.steve();
    automatedPatronBlocksFixture.blockAction(user.getId().toString(), true, false, false);

    Response dryRunWithoutOverride = checkOutFixture.attemptCheckOutByBarcodeDryRun(
        new CheckOutByBarcodeDryRunRequestBuilder()
          .withItemBarcode(item.getBarcode())
          .withUserBarcode(user.getBarcode()))
      .getResponse();

    assertThat(dryRunWithoutOverride, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(dryRunWithoutOverride.getJson(), allOf(
      hasErrorWith(hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE)),
      hasErrorWith(hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE))));

    CheckOutResource dryRunWithOverride = checkOutFixture.checkOutByBarcodeDryRun(
      new CheckOutByBarcodeDryRunRequestBuilder()
        .withItemBarcode(item.getBarcode())
        .withUserBarcode(user.getBarcode())
        .withOverrideBlocks(new CheckOutBlockOverrides()
          .withPatronBlockOverride(new JsonObject())
          .withComment("test comment")
          .create()),
      buildOkapiHeadersWithPermissions(OVERRIDE_PATRON_BLOCK_PERMISSION));

    assertThat(dryRunWithOverride.getResponse(), hasStatus(HTTP_CREATED));
  }

  private static OkapiHeaders buildOkapiHeadersWithPermissions(String permissions) {
    return getOkapiHeadersFromContext()
      .withRequestId(randomId())
      .withOkapiPermissions("[\"" + permissions + "\"]");
  }
}
