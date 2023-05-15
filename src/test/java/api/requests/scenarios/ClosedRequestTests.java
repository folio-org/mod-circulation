package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.ErrorCode.REQUEST_ALREADY_CLOSED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class ClosedRequestTests extends APITests {
  @Test
  void canCancelARequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica());

    IndividualResource requester = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource request =
      requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet, requester, requestDate);

    ZonedDateTime cancelDate = ZonedDateTime.of(2018, 1, 14, 8, 30, 45, 0, UTC);

    final IndividualResource courseReservesCancellationReason
      = cancellationReasonsFixture.courseReserves();

    requestsClient.replace(request.getId(),
      RequestBuilder.from(request)
        .cancelled()
        .withCancellationReasonId(courseReservesCancellationReason.getId())
        .withCancelledByUserId(requester.getId())
        .withCancelledDate(cancelDate));

    IndividualResource getRequest = requestsClient.get(request.getId());

    JsonObject getRepresentation = getRequest.getJson();

    assertThat(getRepresentation.getString("id"), is(request.getId()));
    assertThat(getRepresentation.getString("status"), is("Closed - Cancelled"));
    assertThat(getRepresentation.getString("cancelledByUserId"), is(requester.getId().toString()));
    assertThat(getRepresentation.getString("cancelledDate"), isEquivalentTo(cancelDate));
  }

  @Test
  void cannotEditCancelledRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.jessica());

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource request =
      requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet,
        usersFixture.steve(), requestDate);

    requestsFixture.cancelRequest(request);

    Response response = requestsClient.attemptReplace(request.getId(),
      RequestBuilder.from(request)
        .open()
        .withRequestExpiration(LocalDate.of(2018, 3, 14)));

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Request has already been closed"),
      hasUUIDParameter("id", request.getId()),
      hasCode(REQUEST_ALREADY_CLOSED))));
  }

  @Test
  void cannotEditFulfilledRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    ZonedDateTime requestDate = ZonedDateTime.of(2018, 6, 22, 10, 22, 54, 0, UTC);

    final IndividualResource request =
      requestsFixture.placeItemLevelHoldShelfRequest(smallAngryPlanet,
        steve, requestDate);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve,
      ZonedDateTime.of(2018, 7, 5, 14, 48, 23, 0, UTC));

    Response response = requestsClient.attemptReplace(request.getId(),
      RequestBuilder.from(request)
        .open()
        .withRequestExpiration(LocalDate.of(2018, 3, 14)));

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Request has already been closed"),
      hasUUIDParameter("id", request.getId()),
      hasCode(REQUEST_ALREADY_CLOSED))));
  }

  @Test
  void canCancelARequestLeavingEmptyQueueAndItemStatusChange() {

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .page()
      .fulfillToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequestDate(ClockUtil.getZonedDateTime().minusHours(4))
      .withRequesterId(jessica.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    IndividualResource pagedSmallAngryPlanet = itemsClient.get(smallAngryPlanet);
    assertThat(pagedSmallAngryPlanet, hasItemStatus(PAGED));

    final IndividualResource courseReservesCancellationReason
      = cancellationReasonsFixture.courseReserves();

    requestsClient.replace(requestByJessica.getId(),
        RequestBuilder.from(requestByJessica)
          .cancelled()
          .withCancellationReasonId(courseReservesCancellationReason.getId())
          .withCancelledByUserId(jessica.getId())
          .withCancelledDate(ClockUtil.getZonedDateTime().minusHours(3)));

    IndividualResource availableSmallAngryPlanet = itemsClient.get(smallAngryPlanet);
    assertThat(availableSmallAngryPlanet, hasItemStatus(AVAILABLE));
  }
}
