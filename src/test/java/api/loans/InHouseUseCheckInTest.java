package api.loans;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
import api.support.builders.RequestBuilder;

public class InHouseUseCheckInTest extends APITests {

  @Test
  public void isInHouseUseWhenCheckInServicePointIsPrimaryForHomeLocation() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(true));
  }

  @Test
  public void isInHouseUseWhenItemHasClosedRequests() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.james()));

    IndividualResource recallRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.charlotte()));

    requestsFixture.cancelRequest(recallRequest);

    // Fulfill the page request
    loansFixture.checkOutByBarcode(nod, usersFixture.james());
    loansFixture.checkInByBarcode(nod, checkInServicePointId);

    final CheckInByBarcodeResponse checkInResponse = loansFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(true));

    MultipleJsonRecords requestsForItem = requestsFixture.getRequests(
      queryFromTemplate("itemId=%s", nod.getId()), noLimit(), noOffset());
    assertThat(requestsForItem.totalRecords(), is(2));
  }

  @Test
  public void isNotInHouseUseWhenItemIsRequested() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item
        .withTemporaryLocation(homeLocation.getId()));

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.james()));
    assertThat(requestsFixture.getQueueFor(nod).getTotalRecords(), is(1));

    final CheckInByBarcodeResponse checkInResponse = loansFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(false));
  }

  @Test
  public void isNotInHouseUseWhenCheckInServicePointIsNotServingHomeLocation() {
    final UUID itemServicePointId = servicePointsFixture.cd1().getId();
    final UUID checkInServicePointId = servicePointsFixture.cd2().getId();

    final IndividualResource itemLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(itemServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(itemLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(false));
  }
}
