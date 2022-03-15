package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LocationBuilder;
import io.vertx.core.json.JsonObject;

class LoanCheckInServiceTest {

  private LoanCheckInService loanCheckInService = new LoanCheckInService();

  @Test
  void isInHouseUseWhenServicePointIsPrimaryForHomeLocation() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    JsonObject locationRepresentation = new LocationBuilder()
      .withPrimaryServicePoint(checkInServicePoint)
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(new LocationMapper().toDomain(locationRepresentation));

    assertTrue(loanCheckInService.isInHouseUse(item, createEmptyQueue(),
      checkInRequest));
  }

  @Test
  void isInHouseUseWhenNonPrimaryServicePointServesHomeLocation() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    JsonObject locationRepresentation = new LocationBuilder()
      .withPrimaryServicePoint(UUID.randomUUID())
      .servedBy(checkInServicePoint)
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(new LocationMapper().toDomain(locationRepresentation));

    assertTrue(loanCheckInService.isInHouseUse(item, createEmptyQueue(), checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenItemIsUnavailable() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .checkOut()
      .create();

    JsonObject locationRepresentation = new LocationBuilder()
      .withPrimaryServicePoint(checkInServicePoint)
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(new LocationMapper().toDomain(locationRepresentation));

    assertFalse(loanCheckInService.isInHouseUse(item, createEmptyQueue(),
      checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenItemIsRequested() {
    final UUID checkInServicePoint = UUID.randomUUID();
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    JsonObject locationRepresentation = new LocationBuilder()
      .withPrimaryServicePoint(checkInServicePoint)
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(checkInServicePoint);

    Item item = Item.from(itemRepresentation)
      .withLocation(new LocationMapper().toDomain(locationRepresentation));

    RequestQueue requestQueue = new RequestQueue(Collections
      .singleton(Request.from(new JsonObject())));

    assertFalse(loanCheckInService.isInHouseUse(item, requestQueue, checkInRequest));
  }

  @Test
  void isNotInHouseUseWhenServicePointDoesNotServeHomeLocation() {
    JsonObject itemRepresentation = new ItemBuilder()
      .available()
      .create();

    JsonObject locationRepresentation = new LocationBuilder()
      .withPrimaryServicePoint(UUID.randomUUID())
      .servedBy(UUID.randomUUID())
      .create();

    CheckInByBarcodeRequest checkInRequest = getCheckInRequest(UUID.randomUUID());

    Item item = Item.from(itemRepresentation)
      .withLocation(new LocationMapper().toDomain(locationRepresentation));

    assertFalse(loanCheckInService.isInHouseUse(item, createEmptyQueue(), checkInRequest));
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
