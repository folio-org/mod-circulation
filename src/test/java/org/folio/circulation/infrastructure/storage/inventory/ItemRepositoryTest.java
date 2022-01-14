package org.folio.circulation.infrastructure.storage.inventory;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.infrastructure.storage.inventory.ItemRepository.noLocationMaterialTypeAndLoanTypeInstance;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Temporary tests to pass Sonar checks
 * TODO: delete
 */
@ExtendWith(MockitoExtension.class)
class ItemRepositoryTest {

  @Mock
  CollectionResourceClient holdingsStorage;

  @Mock
  Clients clients;

  @Test
  void shouldReturnEmptyHoldings() {
    when(clients.holdingsStorage())
      .thenReturn(holdingsStorage);
    when(holdingsStorage.getMany(any(), any()))
      .thenReturn(
        completedFuture(succeeded(new Response(200, getJsonAsString(), "contentType"))));
    final ItemRepository itemRepository = noLocationMaterialTypeAndLoanTypeInstance(clients);

    var result = itemRepository.findHoldingsByIds(Arrays.asList("1", "2"))
      .getNow(Result.failed(new ServerErrorFailure("Error")));
    assertTrue(result.succeeded());
    assertNotNull(result.value());
  }

  private String getJsonAsString() {
    JsonObject json = new JsonObject();
    json.put("totalRecords", 0);
    json.put("holdings", new ArrayList<>());
    return json.toString();
  }
}
