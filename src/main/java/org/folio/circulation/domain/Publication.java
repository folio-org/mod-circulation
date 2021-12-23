package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Publication {
  String publisher;
  String place;
  String dateOfPublication;
  String role;
}
