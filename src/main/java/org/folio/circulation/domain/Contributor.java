package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Contributor {
  String name;
  Boolean primary;
}
