package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.Offset.noOffset;
import static org.folio.circulation.support.http.client.Offset.offset;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.http.client.PageLimit.noLimit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class OffsetTest {

  @Test
  public void shouldCalculateOffsetWhenPageLimitAndOffsetValuesNonNull() {
    final Offset offset = offset(14).nextPage(limit(7));

    assertEquals(21, offset.getOffset());
  }

  @Test
  public void shouldCalculateOffsetWhenOffsetValueIsNull() {
    final Offset offset = noOffset().nextPage(limit(10));

    assertEquals(10, offset.getOffset());
  }

  @Test
  public void shouldThrowExceptionWhenPageLimitIsZero() {
    final Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      offset(11).nextPage(noLimit());
    });

    assertTrue(exception.getMessage().contains("Page limit must be non null and greater than 0"));
  }

  @Test
  public void shouldThrowExceptionWhenPageLimitIsNull() {
    final Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      offset(5).nextPage(null);
    });

    assertTrue(exception.getMessage().contains("Page limit must be non null and greater than 0"));
  }
}
