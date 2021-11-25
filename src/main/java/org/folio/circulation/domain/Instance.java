package org.folio.circulation.domain;

import java.util.Collection;
import java.util.List;

import lombok.NonNull;
import lombok.Value;

@Value
public class Instance {
  public static Instance unknown() {
    return new Instance(null, List.of());
  }

  String title;
  @NonNull Collection<Identifier> identifiers;
}
