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

    return consolidateToSinglePredicate(pairs.stream()
      .map(pair -> filterByField(pair.getLeft(), pair.getMiddle(), pair.getRight()))
      .collect(Collectors.toList()));
  }

  private Predicate<JsonObject> filterByField(String field, String term, String operator) {
    return loan -> {
      if (term == null || field == null) {
        return true;
      } else {

        String propertyValue = "";

        //TODO: Should bomb if property does not exist
        if(field.contains(".")) {
          String[] fields = field.split("\\.");

          propertyValue = loan.getJsonObject(String.format("%s", fields[0]))
            .getString(String.format("%s", fields[1]));
        }
        else {
          propertyValue = loan.getString(String.format("%s", field));
        }

        switch(operator) {
          case "=":
            return propertyValue.contains(term);
          case "<>":
            return !propertyValue.contains(term);
          default:
            return false;
        }
      }
    };
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
