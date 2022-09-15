package org.folio.circulation.domain;

import lombok.Value;

@Value
public class IdentifierType {
  String id;
  String name;
  String source;
}
