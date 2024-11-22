package api.support.fakes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getValueByPath;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.json.JsonObject;

public class FakeCQLToJSONInterpreter {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public List<JsonObject> execute(Collection<JsonObject> records, String query,
    WebContext context) {

    var initiallyFilteredRecords = execute(records, query);

    // Routing SP filtering
    String includeRoutingServicePointsParam = context.getStringParameter(
      "includeRoutingServicePoints");
    if (Boolean.parseBoolean(includeRoutingServicePointsParam)) {
        return records.stream()
          .filter(json -> json.containsKey("ecsRequestRouting")
            ? json.getBoolean("ecsRequestRouting") 
            : false)
          .toList();
    }

    return initiallyFilteredRecords;
  }

  public List<JsonObject> execute(Collection<JsonObject> records, String query) {

    final var queryAndSort = splitQueryAndSort(query);
    final var cqlPredicate = new CqlPredicate(queryAndSort.left);

    if(containsSort(queryAndSort)) {
      printDiagnostics(() -> String.format("Search by: %s", queryAndSort.left));
      printDiagnostics(() -> String.format("Sort by: %s", queryAndSort.right));

      return records.stream()
        .filter(cqlPredicate)
        .sorted(sortForQuery(queryAndSort.right))
        .collect(Collectors.toList());
    }
    else {
      printDiagnostics(() -> String.format("Search only by: %s", queryAndSort.left));

      return records.stream()
        .filter(cqlPredicate)
        .collect(Collectors.toList());
    }
  }

  private Comparator<JsonObject> sortForQuery(String sort) {
    String[] sortClauses = sort.split(StringUtils.SPACE);

    return Arrays.stream(sortClauses)
      .map(this::sortClauseToComparator)
      .reduce(Comparator::thenComparing)
      .orElse(null);
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

  private String getPropertyValue(JsonObject record, String field) {
    final Object valueByPath = getValueByPath(record, field.split("\\."));

    return valueByPath != null ? valueByPath.toString() : "";
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
