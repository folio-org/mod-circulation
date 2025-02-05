package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;

class LoanCheckInServiceTest {
  private final LoanCheckInService loanCheckInService = new LoanCheckInService();

  @Test
  void isInHouseUseWhenServicePointIsPrimaryForHomeLocation() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(locationPrimarilyServing(checkInServicePoint));

    assertTrue(loanCheckInService.isInHouseUse(item, createEmptyQueue(),
      checkInRequest));
  }

  @Test
  void isInHouseUseWhenNonPrimaryServicePointServesHomeLocation() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    @NonNull UUID homeServicePointId = UUID.randomUUID();
    Item item = Item.from(itemRepresentation)
      .withLocation(new Location(null, null, null, null,
        List.of(checkInServicePoint), homeServicePointId, false, Institution.unknown(),
        Campus.unknown(), Library.unknown(),
        ServicePoint.unknown(homeServicePointId.toString())));

    assertTrue(loanCheckInService.isInHouseUse(item, createEmptyQueue(), checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenItemIsUnavailable() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .checkOut()
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(locationPrimarilyServing(checkInServicePoint));

    assertFalse(loanCheckInService.isInHouseUse(item, createEmptyQueue(),
      checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenItemIsRequested() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(locationPrimarilyServing(checkInServicePoint));

    RequestQueue requestQueue = new RequestQueue(Collections
      .singleton(Request.from(new JsonObject())));

    assertFalse(loanCheckInService.isInHouseUse(item, requestQueue, checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenServicePointDoesNotServeHomeLocation() {
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(UUID.randomUUID());

    final var homeServicePointId = UUID.randomUUID();

    Item item = Item.from(itemRepresentation)
      .withLocation(locationPrimarilyServing(homeServicePointId));

    assertFalse(loanCheckInService.isInHouseUse(item, createEmptyQueue(), checkInRequest));
  }

  private Location locationPrimarilyServing(@NonNull UUID homeServicePointId) {
    return new Location(null, null, null, null,
      List.of(UUID.randomUUID()), homeServicePointId, false, Institution.unknown(),
      Campus.unknown(), Library.unknown(),
      ServicePoint.unknown(homeServicePointId.toString()));
  }

  private CheckInByBarcodeRequest getCheckInRequest(UUID checkInServicePoint) {
    JsonObject representation = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("barcode")
      .on(ClockUtil.getZonedDateTime())
      .at(checkInServicePoint)
      .create();

    return CheckInByBarcodeRequest.from(representation).value();
  }

  private RequestQueue createEmptyQueue() {
    return new RequestQueue(Collections.emptyList());
  }
}
