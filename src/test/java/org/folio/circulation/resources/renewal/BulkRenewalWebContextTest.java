package org.folio.circulation.resources.renewal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.Context;

class BulkRenewalWebContextTest {

  @Test
  void shouldStoreVertxContext() {
    Context vertxContext = mock(Context.class);
    var ctx = new BulkRenewalWebContext(Map.of(), vertxContext);
    assertSame(vertxContext, ctx.getVertxContext());
  }

  @Test
  void shouldRejectNullVertxContext() {
    assertThrows(NullPointerException.class,
      () -> new BulkRenewalWebContext(Map.of(), null));
  }
}
