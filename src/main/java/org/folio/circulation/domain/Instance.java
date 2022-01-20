package org.folio.circulation.domain;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.stream.Stream;

import lombok.NonNull;
import lombok.Value;

@Value
public class Instance {
  public static Instance unknown() {
    return new Instance(null, null, emptyList(), emptyList(), emptyList(), emptyList());
  }

  String id;
  String title;
  @NonNull Collection<Identifier> identifiers;
  @NonNull Collection<Contributor> contributors;
  @NonNull Collection<Publication> publication;
  @NonNull Collection<String> editions;

  public Stream<String> getContributorNames() {
    return contributors.stream()
      .map(Contributor::getName);
  }

  public String getPrimaryContributorName() {
    return contributors.stream()
      .filter(Contributor::getPrimary)
      .findFirst()
      .map(Contributor::getName)
      .orElse(null);
  }
}
