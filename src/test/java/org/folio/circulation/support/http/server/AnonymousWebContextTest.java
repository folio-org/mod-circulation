package org.folio.circulation.support.http.server;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

@RunWith(MockitoJUnitRunner.class)
public class AnonymousWebContextTest {
  @Mock
  private RoutingContext routingContext;
  @Mock
  private HttpServerRequest request;

  @Before
  public void mockRoutingContext() {
    when(routingContext.request()).thenReturn(request);
  }

  @Test
  public void shouldReturnNullIfNoUserIdHeader() {
    // For scheduled jobs there will be no user id
    // we have to make sure that default value is null
    // otherwise metadata.updatedByUserId might be corrupted
    // when we do crete or update a record in this process
    assertThat(new WebContext(routingContext).getUserId(), nullValue());
  }
}
