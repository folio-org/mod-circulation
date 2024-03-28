package org.folio.circulation.domain;

import static java.util.function.Function.identity;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.Getter;

@Getter
public class MultipleRecordsMap<T> extends MultipleRecords<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Map<String, T> recordsMap;

  public MultipleRecordsMap(MultipleRecords<T> multipleRecords,
    Function<T, String> keyMapper) {

    super(multipleRecords.getRecords(), multipleRecords.getTotalRecords());
    this.recordsMap = records.stream().collect(Collectors.toMap(keyMapper, identity()));
  }

  public T getOrDefault(String key, T defaultRecord) {
    return recordsMap.getOrDefault(key, defaultRecord);
  }
}
