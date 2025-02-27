package api.loans;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.domain.representations.CheckOutByBarcodeDryRunRequest;
import org.junit.jupiter.api.Test;

import api.support.APITests;

class CheckOutByBarcodeDryRunTests extends APITests {

  @Test
  void shouldRetrievePolicyIdsForDryRunCheckOut() {
    var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    var steve = usersFixture.steve();
    var response = checkOutFixture.checkOutByBarcodeDryRun(new CheckOutByBarcodeDryRunRequest(
      smallAngryPlanet.getBarcode(), steve.getBarcode(), null));
    var responseJson = response.getJson();

    assertThat(responseJson.getString("loanPolicyId"), is(loanPoliciesFixture.canCirculateRolling().getId()));
    assertThat(responseJson.getString("overdueFinePolicyId"), is(overdueFinePoliciesFixture.facultyStandard().getId()));
    assertThat(responseJson.getString("lostItemPolicyId"), is(lostItemFeePoliciesFixture.facultyStandard().getId()));
  }
}
