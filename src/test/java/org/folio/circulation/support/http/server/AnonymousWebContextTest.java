package org.folio.circulation.support.http.server;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.RoutingContext;

@ExtendWith(MockitoExtension.class)
class AnonymousWebContextTest {
  @Mock
  private RoutingContext routingContext;
  @Mock
  private HttpServerRequest request;

  @BeforeEach
  public void mockRoutingContext() {
    when(routingContext.request()).thenReturn(request);
  }

  @Test
  void shouldReturnNullIfNoUserIdHeader() {
    // For scheduled jobs there will be no user id
    // we have to make sure that default value is null
    // otherwise metadata.updatedByUserId might be corrupted
    // when we do crete or update a record in this process
    assertThat(new WebContext(routingContext).getUserId(), nullValue());
  }

  @Test
  void getHeadersShouldNotFailOnDuplicateHeaderKeys() {
    when(request.headers()).thenReturn(new HeadersMultiMap()
      .add("test-header", "value1")
      .add("TEST-HEADER", "value2"));

    assertDoesNotThrow(() -> new WebContext(routingContext).getHeaders());
  }
}
