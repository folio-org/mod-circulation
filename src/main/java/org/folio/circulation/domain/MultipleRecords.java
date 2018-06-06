package org.folio.circulation.domain;

import java.util.ArrayList;
import java.util.Collection;

public class MultipleRecords<T> {
  private final Collection<T> records;
  private final Integer totalRecords;

  public static <T> MultipleRecords<T> empty() {
    return new MultipleRecords<>(new ArrayList<>(), 0);
  }

  MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public Collection<T> getRecords() {
    return records;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }
}
