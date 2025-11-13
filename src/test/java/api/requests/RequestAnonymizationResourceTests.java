package api.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.UUID;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.http.IndividualResource;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.resources.RequestAnonymizationResource;
import org.folio.circulation.services.RequestAnonymizationService;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.vertx.core.json.JsonObject;

@Nested
class RequestAnonymizationResourceTest extends APITests {

  @Test
  void register_doesNotThrowAndRegistersRoute() {
    HttpClientInternal internalClient = mock(HttpClientInternal.class);
    HttpClient httpClient = internalClient;

    RequestAnonymizationService service = mock(RequestAnonymizationService.class);
    RequestAnonymizationResource resource =
      new RequestAnonymizationResource(httpClient, service);

    Router router = mock(Router.class);
    Route route = mock(Route.class);

    // RouteRegistration will call router.post(path).handler(handler)
    when(router.post(anyString())).thenReturn(route);
    when(route.handler(any())).thenReturn(route);

    assertDoesNotThrow(() -> resource.register(router));

    // Optional sanity check that our path wiring is correct
    verify(router).post("/request-anonymization/:requestId");
  }
}
