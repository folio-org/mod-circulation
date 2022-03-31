package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.infrastructure.storage.inventory.LocationRepository.using;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import lombok.val;

class LocationRepositoryTest {

  @Test
  void shouldReturnNullWhenLocationIdIsNull() {
    final LocationRepository repository = using(mock(Clients.class),
      new ServicePointRepository(mock(Clients.class)));

    val result = repository.fetchLocationById(null)
      .getNow(Result.failed(new ServerErrorFailure("Error")));

    assertTrue(result.succeeded());
    assertNull(result.value());
  }
}
