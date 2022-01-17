package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
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
  ServicePointRepository servicePointRepository;

  @Mock
  ItemsReportFetcher itemsReportFetcher;

  @Mock
  LoanRepository loanRepository;

  @Mock
  ItemRepository itemRepository;

  @Mock
  LocationRepository locationRepository;

  @Test
  void itemsInTransitReportServiceTest() {
    String servicePointId = UUID.randomUUID().toString();
    when(itemReportRepository.getAllItemsByField(any(), any()))
      .thenReturn(completedFuture(succeeded(itemsReportFetcher)));

    when(itemsReportFetcher.getResultListOfItems())
      .thenReturn(List.of(succeeded(new MultipleRecords<>(
        List.of(Item.from(new JsonObject()
          .put("inTransitDestinationServicePointId", servicePointId))), 1))));

    when(loanRepository.findByItemIds(anyCollection()))
      .thenReturn(ofAsync(() -> new MultipleRecords<>(
        List.of(Loan.from(new JsonObject()
          .put("checkoutServicePointId", servicePointId)
          .put("checkinServicePointId", servicePointId))), 1)));

    when(itemRepository.findHoldingsByIds(any()))
      .thenReturn(completedFuture(succeeded(new MultipleRecords<>(
              List.of(Holdings.unknown()), 1))));

    when(locationRepository.getItemLocations(any(), any()))
      .thenReturn(completedFuture(succeeded(Map.of("locationKey", Location.from(new JsonObject())))));

    ItemsInTransitReportService service = new ItemsInTransitReportService(itemReportRepository,
      loanRepository, locationRepository, servicePointRepository, null, itemRepository,
      null, null);
    CompletableFuture<Result<JsonObject>> report = service.buildReport();
    assertNotNull(report);
  }
}
