package org.folio.circulation.infrastructure.storage.inventory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.circulation.domain.Request;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

/**
 * Temporary tests to pass Sonar checks
 */
@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

  @Mock
  CollectionResourceClient usersStorageClient;

  @Mock
  Clients clients;

  @Test
  void shouldReturnEmptyUsers() {
    when(clients.usersStorage())
      .thenReturn(usersStorageClient);

    final UserRepository userRepository = new UserRepository(clients);

    var result = userRepository.findUsersByRequests(List.of(Request.from(new JsonObject())))
      .getNow(Result.failed(new ServerErrorFailure("Error")));

    assertTrue(result.succeeded());
    assertNotNull(result.value());
  }
}
