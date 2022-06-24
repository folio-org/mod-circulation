package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Identifier {
  String identifierTypeId;
  String value;
}
