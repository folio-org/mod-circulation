package org.folio.circulation.domain;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.stream.Stream;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class Instance {
  public static Instance unknown() {
    return new Instance(null, null,null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
  }

  @ToString.Include
  String id;
  @ToString.Include
  String hrid;
  @ToString.Include
  String title;
  @NonNull Collection<Identifier> identifiers;
  @NonNull Collection<Contributor> contributors;
  @NonNull Collection<Publication> publication;
  @NonNull Collection<String> editions;
  @NonNull Collection<String> physicalDescriptions;
  @NonNull Collection<SeriesStatement> series;

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

  public Stream<String> getSeriesStatementValues() {
    return series.stream()
      .map(SeriesStatement::getValue);
  }

  // TODO: replace this stub with proper implementation
  public boolean isNotFound() {
    return id == null;
  }

}
