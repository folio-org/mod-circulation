package org.folio.circulation.support.http.server;

import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

@RunWith(MockitoJUnitRunner.class)
public class WebContextTest {
  @Mock
  private RoutingContext routingContext;
  @Mock
  private MultiMap headers;
  @Mock
  private HttpServerRequest request;

  @Before
  public void mockRoutingContext() {
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(headers);
  }

  @Test
  public void shouldReturnNullIfNoUserIdHeader() {
    // For scheduled jobs there will be no user id
    // we have to make sure that default value is null
    // otherwise metadata.updatedByUserId might be corrupted
    // when we do crete or update a record in this process

    when(headers.contains(USER_ID)).thenReturn(false);

    assertThat(new WebContext(routingContext).getUserId(), nullValue());
  }
}
