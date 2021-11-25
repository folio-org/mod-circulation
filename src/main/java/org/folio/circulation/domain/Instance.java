package org.folio.circulation.domain;

import static java.util.Collections.emptyList;

import java.util.Collection;

import lombok.NonNull;
import lombok.Value;

@Value
public class Instance {
  public static Instance unknown() {
    return new Instance(null, emptyList(), emptyList());
  }

  String title;
  @NonNull Collection<Identifier> identifiers;
  @NonNull Collection<Contributor> contributors;
}
