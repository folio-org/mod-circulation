package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.IN_TRANSIT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.RequestMatchers.hasPosition;
import static api.support.matchers.RequestMatchers.isItemLevel;
import static api.support.matchers.RequestMatchers.isOpenAwaitingPickup;
import static api.support.matchers.RequestMatchers.isOpenNotYetFilled;
import static api.support.matchers.RequestMatchers.isTitleLevel;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.InstanceBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;

class HoldShelfFulfillmentTests extends APITests {
  @AfterEach
  public void afterEach() {
    configurationsFixture.deleteTlrFeatureConfig();
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void itemIsReadyForPickUpWhenCheckedInAtPickupServicePoint(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));

    assertThat("awaiting pickup item should not have a destination",
      smallAngryPlanet.getJson().containsKey("inTransitDestinationServicePointId"),
      is(false));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void tlrRequestIsPositionedCorrectlyInUnifiedQueue(int checkedInItemNumber) {
    configurationsFixture.enableTlrFeature();

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();

    List<IndividualResource> items = createMultipleItemsForTheSameInstance(3);
    IndividualResource smallAngryPlanet1 = items.get(0);
    IndividualResource smallAngryPlanet2 = items.get(1);
    UUID instanceId = ((ItemResource) smallAngryPlanet1).getInstanceId();

    IndividualResource james = usersFixture.james();
    IndividualResource steve = usersFixture.steve();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet1, james);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet2, james);

    // ILR request by Steve, should be #1 in the unified queue
    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet1, steve, ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    // TLR request by Jessica, should be #2 in the unified queue
    IndividualResource requestByJessica = requestsFixture.placeTitleLevelHoldShelfRequest(
      instanceId, jessica, ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    // ILR request by Jessica, should be #3 in the unified queue
    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet2, charlotte, ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    assertThat(requestsClient.getById(requestBySteve.getId()).getJson(), hasPosition(1));
    assertThat(requestsClient.getById(requestByJessica.getId()).getJson(), hasPosition(2));
    assertThat(requestsClient.getById(requestByCharlotte.getId()).getJson(), hasPosition(3));

    // Depending on which item is checked in, either Steve's (ILR) or Jessica's (TLR) request
    // should be fulfilled
    checkInFixture.checkInByBarcode(
      checkedInItemNumber == 1 ? smallAngryPlanet1 : smallAngryPlanet2,
      ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    if (checkedInItemNumber == 1) {
      // Steve's request should be fulfilled. All requests should keep their positions until
      // re-positioning is triggered by check-out, cancellation etc.

      Response updatedRequestBySteve = requestsClient.getById(requestBySteve.getId());
      assertThat(updatedRequestBySteve.getJson(), isItemLevel());
      assertThat(updatedRequestBySteve.getJson(), isOpenAwaitingPickup());
      assertThat(updatedRequestBySteve.getJson(), hasPosition(1));

      Response updatedRequestByJessica = requestsClient.getById(requestByJessica.getId());
      assertThat(updatedRequestByJessica.getJson(), isTitleLevel());
      assertThat(updatedRequestByJessica.getJson(), isOpenNotYetFilled());
      assertThat(updatedRequestByJessica.getJson(), hasPosition(2));

      Response updatedRequestByCharlotte = requestsClient.getById(requestByCharlotte.getId());
      assertThat(updatedRequestByCharlotte.getJson(), isItemLevel());
      assertThat(updatedRequestByCharlotte.getJson(), isOpenNotYetFilled());
      assertThat(updatedRequestByCharlotte.getJson(), hasPosition(3));

      // Item #1 should be awaiting pickup and shouldn't have a destination service point
      // because it was checked in at a pickup service point
      smallAngryPlanet1 = itemsClient.get(smallAngryPlanet1.getId());
      assertThat(smallAngryPlanet1, hasItemStatus(AWAITING_PICKUP));
      assertThat("awaiting pickup item should not have a destination",
        smallAngryPlanet1.getJson().containsKey("inTransitDestinationServicePointId"),
        is(false));

      // Item #2 should still be checked out
      smallAngryPlanet2 = itemsClient.get(smallAngryPlanet2.getId());
      assertThat(smallAngryPlanet2, hasItemStatus(CHECKED_OUT));
    }

    if (checkedInItemNumber == 2) {
      // Jessica's request should be fulfilled. All requests should keep their positions until
      // re-positioning is triggered by check-out, cancellation etc.

      Response updatedRequestBySteve = requestsClient.getById(requestBySteve.getId());
      assertThat(updatedRequestBySteve.getJson(), isItemLevel());
      assertThat(updatedRequestBySteve.getJson(), isOpenNotYetFilled());
      assertThat(updatedRequestBySteve.getJson(), hasPosition(1));

      Response updatedRequestByJessica = requestsClient.getById(requestByJessica.getId());
      assertThat(updatedRequestByJessica.getJson(), isTitleLevel());
      assertThat(updatedRequestByJessica.getJson(), isOpenAwaitingPickup());
      assertThat(updatedRequestByJessica.getJson(), hasPosition(2));

      Response updatedRequestByCharlotte = requestsClient.getById(requestByCharlotte.getId());
      assertThat(updatedRequestByCharlotte.getJson(), isItemLevel());
      assertThat(updatedRequestByCharlotte.getJson(), isOpenNotYetFilled());
      assertThat(updatedRequestByCharlotte.getJson(), hasPosition(3));

      // Item #1 should still be checked out
      smallAngryPlanet1 = itemsClient.get(smallAngryPlanet1.getId());
      assertThat(smallAngryPlanet1, hasItemStatus(CHECKED_OUT));

      // Item #2 should be awaiting pickup and shouldn't have a destination service point
      // because it was checked in at a pickup service point
      smallAngryPlanet2 = itemsClient.get(smallAngryPlanet2.getId());
      assertThat(smallAngryPlanet2, hasItemStatus(AWAITING_PICKUP));
      assertThat("awaiting pickup item should not have a destination",
        smallAngryPlanet2.getJson().containsKey("inTransitDestinationServicePointId"),
        is(false));
    }
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void canBeCheckedOutToRequestingPatronWhenReadyForPickup(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica,
      ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());
    assertThat(request.getJson(), isItemLevel());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  void canBeCheckedOutToPatronRequestingTitleWhenReadyForPickup() {
    configurationsFixture.enableTlrFeature();

    // TODO: Should be completed in scope of CIRC-1297
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void checkInAtDifferentServicePointPlacesItemInTransit(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_IN_TRANSIT));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(IN_TRANSIT));

    final String destinationServicePoint = smallAngryPlanet.getJson()
      .getString("inTransitDestinationServicePointId");

    assertThat("in transit item should have a destination",
      destinationServicePoint, is(pickupServicePoint.getId()));
  }

  @Test
  void checkInItemWithTlrRequestAtDifferentServicePointPlacesItemInTransit() {
    configurationsFixture.enableTlrFeature();

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    List<IndividualResource> items = createMultipleItemsForTheSameInstance(2);
    IndividualResource smallAngryPlanet1 = items.get(0);
    IndividualResource smallAngryPlanet2 = items.get(1);
    UUID instanceId = ((ItemResource) smallAngryPlanet1).getInstanceId();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet1, james);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet2, james);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet1, steve, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    IndividualResource requestByJessica = requestsFixture.placeTitleLevelHoldShelfRequest(
      instanceId, jessica, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet2,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());
    assertThat(request.getJson(), isTitleLevel());

    assertThat(request.getJson().getString("status"), is(OPEN_IN_TRANSIT));

    smallAngryPlanet2 = itemsClient.get(smallAngryPlanet2);

    assertThat(smallAngryPlanet2, hasItemStatus(IN_TRANSIT));

    final String destinationServicePoint = smallAngryPlanet2.getJson()
      .getString("inTransitDestinationServicePointId");

    assertThat("in transit item should have a destination",
      destinationServicePoint, is(pickupServicePoint.getId()));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void canBeCheckedOutToRequestingPatronWhenInTransit(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica,
      ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  void canBeCheckedOutToRequestingPatronWhenInTransit() {
    configurationsFixture.enableTlrFeature();

    // TODO: Should be completed in scope of CIRC-1297
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void itemIsReadyForPickUpWhenCheckedInAtPickupServicePointAfterTransit(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet,
      ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));

    assertThat("awaiting pickup item should not have a destination",
      smallAngryPlanet.getJson().containsKey("inTransitDestinationServicePointId"),
      is(false));
  }

  @Test
  void itemWithTlrRequestIsReadyForPickUpWhenCheckedInAtPickupServicePointAfterTransit() {
    configurationsFixture.enableTlrFeature();

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    List<IndividualResource> items = createMultipleItemsForTheSameInstance(2);
    IndividualResource smallAngryPlanet1 = items.get(0);
    IndividualResource smallAngryPlanet2 = items.get(1);
    UUID instanceId = ((ItemResource) smallAngryPlanet1).getInstanceId();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet1, james);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet2, james);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet1, steve, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    IndividualResource requestByJessica = requestsFixture.placeTitleLevelHoldShelfRequest(
      instanceId, jessica, ClockUtil.getZonedDateTime(),
      pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet2,
      ClockUtil.getZonedDateTime(), checkInServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet2,
      ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());
    assertThat(request.getJson(), isTitleLevel());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet2 = itemsClient.get(smallAngryPlanet2);

    assertThat(smallAngryPlanet2, hasItemStatus(AWAITING_PICKUP));

    assertThat("awaiting pickup item should not have a destination",
      smallAngryPlanet2.getJson().containsKey("inTransitDestinationServicePointId"),
      is(false));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void cannotCheckOutToOtherPatronWhenRequestIsAwaitingPickup(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasUserBarcodeParameter(rebecca))));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  void cannotCheckOutToOtherPatronWhenTlrRequestIsAwaitingPickup() {
    configurationsFixture.enableTlrFeature();

    // TODO: Should be completed in scope of CIRC-1297
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void cannotCheckOutToOtherPatronWhenRequestIsInTransitForPickup(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime(),
      requestServicePoint.getId());

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .at(checkInServicePoint.getId()));

    Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasUserBarcodeParameter(rebecca))));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_IN_TRANSIT));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(IN_TRANSIT));
  }

  @Test
  void cannotCheckOutToOtherPatronWhenTlrRequestIsInTransitForPickup() {
    configurationsFixture.enableTlrFeature();
    // TODO: Should be completed in scope of CIRC-1297
  }

  private List<IndividualResource> createMultipleItemsForTheSameInstance(int size) {
    UUID instanceId = UUID.randomUUID();
    InstanceBuilder sapInstanceBuilder = itemsFixture.instanceBasedUponSmallAngryPlanet()
      .withId(instanceId);

    return IntStream.range(0, size)
      .mapToObj(num -> itemsFixture.basedUponSmallAngryPlanet(
        holdingsBuilder -> holdingsBuilder.forInstance(instanceId),
        instanceBuilder -> sapInstanceBuilder,
        itemBuilder -> itemBuilder.withBarcode("0000" + num)))
      .collect(Collectors.toList());
  }

  private enum TlrFeatureStatus {
    ENABLED,
    DISABLED,
    NOT_CONFIGURED
  }

  private void reconfigureTlrFeature(TlrFeatureStatus tlrFeatureStatus) {
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      configurationsFixture.enableTlrFeature();
    }
    else if (tlrFeatureStatus == TlrFeatureStatus.DISABLED) {
      configurationsFixture.disableTlrFeature();
    }
    else {
      configurationsFixture.deleteTlrFeatureConfig();
    }
  }
}
