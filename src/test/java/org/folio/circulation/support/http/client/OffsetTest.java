package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.Offset.noOffset;
import static org.folio.circulation.support.http.client.Offset.offset;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.http.client.PageLimit.noLimit;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class OffsetTest {
  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

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
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Page limit must be non null and greater than 0");

    offset(11).nextPage(noLimit());
  }

  @Test
  public void shouldThrowExceptionWhenPageLimitIsNull() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Page limit must be non null and greater than 0");

    offset(5).nextPage(null);
  }
}
