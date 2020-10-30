package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.Offset.noOffset;
import static org.folio.circulation.support.http.client.Offset.offset;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.http.client.PageLimit.noLimit;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
  public void shouldCalculateOffsetWhenPageLimitValueIsNull() {
    final Offset offset = offset(11).nextPage(noLimit());

    assertEquals(11, offset.getOffset());
  }

  @Test
  public void shouldCalculateOffsetWhenPageLimitAndOffsetValuesAreNull() {
    final Offset offset = noOffset().nextPage(noLimit());

    assertEquals(0, offset.getOffset());
  }
}
