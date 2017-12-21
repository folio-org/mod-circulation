package org.folio.circulation.api.fakes;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FakeCQLToJSONInterpreter {
  private final boolean diagnosticsEnabled;

  public FakeCQLToJSONInterpreter() {
    this(false);
  }

  public FakeCQLToJSONInterpreter(boolean diagnosticsEnabled) {
    this.diagnosticsEnabled = diagnosticsEnabled;
  }

  Predicate<JsonObject> filterForQuery(String query) {

    if(StringUtils.isBlank(query)) {
      return t -> true;
    }

    List<ImmutableTriple<String, String, String>> pairs =
      Arrays.stream(query.split(" and "))
        .map( pairText -> {
          String[] split = pairText.split("=|<>");
          String searchField = split[0]
            .replaceAll("\"", "");

          String searchTerm = split[1]
            .replaceAll("\"", "")
            .replaceAll("\\*", "");

          if(pairText.contains("=")) {
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
    return loan -> {
      boolean result = false;
      String propertyValue = "";

      if (term == null || field == null) {
        result = true;
      } else {
        propertyValue = getPropertyValue(loan, field);

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
          loan.encodePrettily(), field, operator, term, result, propertyValue));
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

  private String getPropertyValue(JsonObject loan, String field) {
    //TODO: Should bomb if property does not exist
    if(field.contains(".")) {
      String[] fields = field.split("\\.");

      return loan.getJsonObject(String.format("%s", fields[0]))
        .getString(String.format("%s", fields[1]));
    }
    else {
      return loan.getString(String.format("%s", field));
    }
  }

  private Predicate<JsonObject> consolidateToSinglePredicate(
    Collection<Predicate<JsonObject>> predicates) {

    return predicates.stream().reduce(Predicate::and).orElse(t -> false);
  }

  public List<JsonObject> filterByQuery(Collection<JsonObject> records, String query) {
    return records.stream()
      .filter(filterForQuery(query))
      .collect(Collectors.toList());
  }
}
