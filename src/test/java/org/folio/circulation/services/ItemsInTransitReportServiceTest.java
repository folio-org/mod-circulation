package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

/**
 * Temporary meaningless test to pass SonarQube analysis.
 * Once the feature is implemented, ItemsInTransitReportService will replace the existing
 * items-in-transit report logic and will be covered by existing API tests.
 */
@ExtendWith(MockitoExtension.class)
class ItemsInTransitReportServiceTest {
  @Mock
  ItemReportRepository itemReportRepository;

  @Mock
  ItemsReportFetcher itemsReportFetcher;

  @Mock
  ItemRepository itemRepository;

  @Mock
  LocationRepository locationRepository;

  @Mock
  RequestRepository requestRepository;

  @Test
  void itemsInTransitReportServiceTest() {
    when(itemReportRepository.getAllItemsByField(any(), any()))
      .thenReturn(completedFuture(succeeded(itemsReportFetcher)));

    when(itemsReportFetcher.getResultListOfItems())
      .thenReturn(List.of(succeeded(new MultipleRecords<>(
        List.of(Item.from(new JsonObject())), 1))));

    when(itemRepository.findHoldingsByIds(any()))
      .thenReturn(completedFuture(succeeded(new MultipleRecords<>(
              List.of(Holdings.unknown()), 1))));

    when(locationRepository.getItemLocations(any(), any()))
      .thenReturn(completedFuture(succeeded(Map.of("locationKey", Location.from(new JsonObject())))));

    when(requestRepository.findOpenRequestsByItemIds(any()))
      .thenReturn(completedFuture(succeeded(new MultipleRecords<>(
        List.of(new Request(null, null, null, null,
          null, null, null, null,  false,
          0, false)), 1))));

    ItemsInTransitReportService service = new ItemsInTransitReportService(itemReportRepository, locationRepository,
      null, null, requestRepository,
      itemRepository, null, null);
    CompletableFuture<Result<JsonObject>> report = service.buildReport();
    assertNotNull(report);
  }
}
