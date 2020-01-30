package api.support.fakes;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeCQLToJSONInterpreter {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public List<JsonObject> execute(Collection<JsonObject> records, String query) {
    ImmutablePair<String, String> queryAndSort = splitQueryAndSort(query);

    if(containsSort(queryAndSort)) {
      printDiagnostics(() -> String.format("Search by: %s", queryAndSort.left));
      printDiagnostics(() -> String.format("Sort by: %s", queryAndSort.right));

      return records.stream()
        .filter(filterForQuery(queryAndSort.left))
        .sorted(sortForQuery(queryAndSort.right))
        .collect(Collectors.toList());
    }
    else {
      printDiagnostics(() -> String.format("Search only by: %s", queryAndSort.left));

      return records.stream()
        .filter(filterForQuery(queryAndSort.left))
        .collect(Collectors.toList());
    }
  }

  private Comparator<JsonObject> sortForQuery(String sort) {
    String[] sortClauses = sort.split(StringUtils.SPACE);

    return Arrays.stream(sortClauses)
      .map(this::sortClauseToComparator)
      .reduce(Comparator::thenComparing)
      .get();
  }

  private Comparator<JsonObject> sortClauseToComparator(String sort) {
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
          String[] split = pairText.split("==|=|<>|<|>");

          printDiagnostics(() -> String.format("Split clause: %s",
            String.join(", ", split)));

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
          else if(pairText.contains("<>")) {
            return new ImmutableTriple<>(searchField, searchTerm, "<>");
          }
          else if(pairText.contains("<")) {
            return new ImmutableTriple<>(searchField, searchTerm, "<");
          }
          else if(pairText.contains(">")) {
            return new ImmutableTriple<>(searchField, searchTerm, ">");
          }
          else {
            //Should fail completely
            return new ImmutableTriple<>(searchField, searchTerm, "");
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
      final boolean result;
      final String propertyValue;

      if (term == null || field == null) {
        printDiagnostics(() -> "Either term or field are null, aborting filtering");
        return true;
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
            case ">":
              result = propertyValue.compareTo(cleanTerm) > 0;
              break;
            case "<":
              result = propertyValue.compareTo(cleanTerm) < 0;
              break;
            default:
              result = false;
          }
        }

        printDiagnostics(() -> String.format("Filtering %s by %s %s %s: %s (value: %s)",
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
    try {
      if(field.contains(".")) {
        String[] fields = field.split("\\.");

        if(!record.containsKey(String.format("%s", fields[0]))) {
          return null;
        }

        return record.getJsonObject(String.format("%s", fields[0]))
          .getValue(String.format("%s", fields[1].trim()), "").toString();
      }
      else {
        return record.getValue(String.format("%s", field.trim()), "").toString();
      }
    }
    catch(Exception e) {
      throw new IllegalArgumentException(String.format(
        "Cannot get value of %s from %s", field,
        record.encodePrettily()));
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

  private void printDiagnostics(Supplier<String> diagnosticTextSupplier) {
    log.debug("{}", diagnosticTextSupplier);
  }
}
