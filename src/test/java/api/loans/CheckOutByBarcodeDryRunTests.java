package api.loans;

import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasProxyUserBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.domain.representations.CheckOutByBarcodeDryRunRequest;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.UserBuilder;

class CheckOutByBarcodeDryRunTests extends APITests {

  @Test
  void shouldRetrievePolicyIds() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var steve = usersFixture.steve();
    var response = checkOutFixture.checkOutByBarcodeDryRun(new CheckOutByBarcodeDryRunRequest(
      smallAngryPlanet.getBarcode(), steve.getBarcode(), null));
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
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null));

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
        james.getBarcode(), steve.getBarcode()));

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
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null));

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
      new CheckOutByBarcodeDryRunRequest(smallAngryPlanet.getBarcode(), steve.getBarcode(), null));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }
}
