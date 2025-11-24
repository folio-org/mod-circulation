package api.requests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.circulation.resources.RequestAnonymizationResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

@Nested
class RequestAnonymizationResourceTests extends APITests {

  @Test
  void registerDoesNotThrowAndRegistersRoute() {
    HttpClientInternal internalClient = mock(HttpClientInternal.class);
    HttpClient httpClient = internalClient;

    RequestAnonymizationResource resource = new RequestAnonymizationResource(httpClient);

    Router router = mock(Router.class);
    Route route = mock(Route.class);

    when(router.post(anyString())).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    assertDoesNotThrow(() -> resource.register(router));

    verify(router).post("/request-anonymization/:requestId");
  }
}
