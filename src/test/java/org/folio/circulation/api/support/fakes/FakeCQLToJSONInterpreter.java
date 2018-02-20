package org.folio.circulation.api.support.fakes;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FakeCQLToJSONInterpreter {
  private final boolean diagnosticsEnabled;

  public FakeCQLToJSONInterpreter(boolean diagnosticsEnabled) {
    this.diagnosticsEnabled = diagnosticsEnabled;
  }

  public List<JsonObject> execute(Collection<JsonObject> records, String query) {
    ImmutablePair<String, String> queryAndSort = splitQueryAndSort(query);

    if(containsSort(queryAndSort)) {
      if(diagnosticsEnabled) {
        System.out.println(String.format("Search by: %s", queryAndSort.left));
        System.out.println(String.format("Sort by: %s", queryAndSort.right));
      }

      return records.stream()
        .filter(filterForQuery(queryAndSort.left))
        .sorted(sortForQuery(queryAndSort.right))
        .collect(Collectors.toList());
    }
    else {
      if(diagnosticsEnabled) {
        System.out.println(String.format("Search only by: %s", queryAndSort.left));
      }

      return records.stream()
        .filter(filterForQuery(queryAndSort.left))
        .collect(Collectors.toList());
    }
  }

  private Comparator<? super JsonObject> sortForQuery(String sort) {
    if(StringUtils.contains(sort, "/")) {
      String propertyName = StringUtils.substring(sort, 0,
        StringUtils.lastIndexOf(sort, "/")).trim();

      String ordering = StringUtils.substring(sort,
        StringUtils.lastIndexOf(sort, "/") + 1).trim();

      if(StringUtils.containsIgnoreCase(ordering, "descending")) {
        return Comparator.comparing(
          (JsonObject record) -> getPropertyValue(record, propertyName))
          .reversed();
      }
      else {
        return Comparator.comparing(record -> getPropertyValue(record, propertyName));
      }
    }
    else {
      return Comparator.comparing(record -> getPropertyValue(record, sort));
    }
  }

  private boolean containsSort(ImmutablePair<String, String> queryAndSort) {
    return StringUtils.isNotBlank(queryAndSort.right);
  }

  private Predicate<JsonObject> filterForQuery(String query) {
    if(StringUtils.isBlank(query)) {
      return t -> true;
    }

    List<ImmutableTriple<String, String, String>> pairs =
      Arrays.stream(query.split(" and "))
        .map( pairText -> {
          String[] split = pairText.split("==|=|<>");
          String searchField = split[0]
            .replaceAll("\"", "");

          String searchTerm = split[1]
            .replaceAll("\"", "")
            .replaceAll("\\*", "");

          if(pairText.contains("==")) {
            return new ImmutableTriple<>(searchField, searchTerm, "==");
          }
          else if(pairText.contains("=")) {
            return new ImmutableTriple<>(searchField, searchTerm, "=");
          }
          else {
            return new ImmutableTriple<>(searchField, searchTerm, "<>");
          }
        })
        .collect(Collectors.toList());

    return consolidateToSinglePredicate(
      pairs.stream()
      .map(pair -> filterByField(pair.getLeft(), pair.getMiddle(), pair.getRight()))
      .collect(Collectors.toList()));
  }

  private Predicate<JsonObject> filterByField(String field, String term, String operator) {
    return record -> {
      boolean result = false;
      String propertyValue = "";

      if (term == null || field == null) {
        result = true;
      }
      else {
        propertyValue = getPropertyValue(record, field);

        String cleanTerm = removeBrackets(term);

        if (cleanTerm.contains("or")) {
          Collection<String> acceptableValues = Arrays.stream(cleanTerm.split(" or "))
            .collect(Collectors.toList());

          Predicate<String> predicate = acceptableValues.stream()
            .map(this::filter)
            .reduce(Predicate::or)
            .orElse(t -> false);

          result = predicate.test(propertyValue);
        } else {
          if(propertyValue == null) {
            return false;
          }

          switch (operator) {
            case "==":
              result = propertyValue.equals(cleanTerm);
              break;
            case "=":
              result = propertyValue.contains(cleanTerm);
              break;
            case "<>":
              result = !propertyValue.contains(cleanTerm);
              break;
            default:
              result = false;
          }
        }
      }

      if(diagnosticsEnabled) {
        System.out.println(String.format("Filtering %s by %s %s %s: %s (value: %s)",
          record.encodePrettily(), field, operator, term, result, propertyValue));
      }

      return result;
    };
  }

  private String removeBrackets(String term) {
    return term.replace("(", "").replace(")", "");
  }

  private Predicate<String> filter(String term) {
    return v -> v.contains(term);
  }

  private String getPropertyValue(JsonObject record, String field) {
    //TODO: Should bomb if property does not exist
    if(field.contains(".")) {
      String[] fields = field.split("\\.");

      return record.getJsonObject(String.format("%s", fields[0]))
        .getString(String.format("%s", fields[1]));
    }
    else {
      return record.getString(String.format("%s", field));
    }
  }

  private Predicate<JsonObject> consolidateToSinglePredicate(
    Collection<Predicate<JsonObject>> predicates) {

    return predicates.stream().reduce(Predicate::and).orElse(t -> false);
  }

  private ImmutablePair<String, String> splitQueryAndSort(String query) {
    if(StringUtils.containsIgnoreCase(query, "sortby")) {
      int sortByIndex = StringUtils.lastIndexOfIgnoreCase(query, "sortby");

      String searchOnly = StringUtils.substring(query, 0, sortByIndex).trim();
      String sortOnly = StringUtils.substring(query, sortByIndex + 6).trim();

      return new ImmutablePair<>(searchOnly, sortOnly);
    }
    else {
      return new ImmutablePair<>(query, "");
    }
  }
}
