package org.folio.circulation;

import org.joda.time.DateTime;

@FunctionalInterface
public interface Clock {
  DateTime now();
}
