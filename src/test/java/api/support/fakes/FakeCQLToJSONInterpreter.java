package api.support.fakes;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FakeCQLToJSONInterpreter {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Map<String, BiFunction<Object, String, Boolean>> OPERATORS = buildOperatorsMap();
  private static final String OPERATOR_SPLIT_REGEX = String.join("|", OPERATORS.keySet());

  private static Map<String, BiFunction<Object, String, Boolean>> buildOperatorsMap() {
    final Map<String, BiFunction<Object, String, Boolean>> operators = new HashMap<>();

    operators.put("==", operator(Objects::equals));
    operators.put("=", operator(String::contains));
    operators.put("<>", operator((actual, expected) -> !actual.contains(expected)));
    operators.put(">", operator((actual, expected) -> actual.compareTo(expected) > 0));
    operators.put("<", operator((actual, expected) -> actual.compareTo(expected) < 0));

    return Collections.unmodifiableMap(operators);
  }

  private static BiFunction<Object, String, Boolean> operator(BiFunction<String, String, Boolean> predicate) {
    return (value, expectedValue) -> {
      if (value == null) {
        return false;
      }

      if (!(value instanceof JsonArray)) {
        return predicate.apply(value.toString(), expectedValue);
      }

      // support basic searches against array
      final JsonArray jsonArray = (JsonArray) value;
      final String arrayToken = jsonArray.stream()
        .map(obj -> {
          if (obj == null) {
            return null;
          }

          if (obj instanceof JsonObject) {
            return ((JsonObject) obj).getMap().values().stream()
              .map(Objects::toString)
              .collect(Collectors.joining(" "));
          }

          return obj.toString();
        }).collect(Collectors.joining(" "));

      return predicate.apply(arrayToken, expectedValue);
    };
  }

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
        .map(pairText -> {
          String[] split = pairText.split(OPERATOR_SPLIT_REGEX);

          printDiagnostics(() -> String.format("Split clause: %s",
            String.join(", ", split)));

          String searchField = split[0]
            .replaceAll("\"", "");

          String searchTerm = split[1]
            .replaceAll("\"", "")
            .replaceAll("\\*", "");

          final String operator = OPERATORS.keySet().stream()
            .filter(pairText::contains)
            .findFirst()
            .orElse("");

          return new ImmutableTriple<>(searchField, searchTerm, operator);
        }).collect(Collectors.toList());

    return consolidateToSinglePredicate(pairs.stream()
        .map(pair -> filterByField(pair.getLeft(), pair.getMiddle(), pair.getRight()))
        .collect(Collectors.toList()));
  }

  private Predicate<JsonObject> filterByField(String field, String term, String operator) {
    return record -> {
      if (term == null || field == null) {
        printDiagnostics(() -> "Either term or field are null, aborting filtering");
        return true;
      }
      final Object propertyValue = getPropertyValue(record, field, null);
      final String cleanTerm = removeBrackets(term);

      final List<String> acceptableValues = cleanTerm.contains(" or ")
        ? Arrays.asList(cleanTerm.split(" or "))
        : Collections.singletonList(cleanTerm);

      final boolean result = acceptableValues.stream()
        .anyMatch(value -> filter(propertyValue, operator, value));

      printDiagnostics(() -> String.format("Filtering %s by %s %s %s: %s (value: %s)",
        record.encodePrettily(), field, operator, term, result, propertyValue));

      return result;
    };
  }

  private String removeBrackets(String term) {
    return term.replace("(", "").replace(")", "");
  }

  private boolean filter(Object actualValue, String operator, String expected) {
    final BiFunction<Object, String, Boolean> operatorFunction = OPERATORS
      .getOrDefault(operator, (f, s) -> false);

    return operatorFunction.apply(actualValue, expected);
  }

  private String getPropertyValue(JsonObject record, String field) {
    return getPropertyValue(record, field, "").toString();
  }


  private Object getPropertyValue(JsonObject record, String field, Object def) {
    Object value = record.getValue(field);
    if (field.contains(".")) {
      String[] fields = field.split("\\.");
      JsonObject currentObject = record;

      for (int i = 0; i < fields.length - 1; i++) {
        currentObject = currentObject.getJsonObject(fields[i], new JsonObject());
      }

      value = currentObject.getValue(fields[fields.length - 1]);
    }

    return value == null ? def : value;
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
