package org.folio.circulation;

import java.time.ZonedDateTime;

@FunctionalInterface
public interface Clock {
  ZonedDateTime now();
}
