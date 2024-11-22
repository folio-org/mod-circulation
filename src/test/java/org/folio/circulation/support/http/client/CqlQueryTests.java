package org.folio.circulation.support.http.client;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.CqlQuery.greaterThan;
import static org.folio.circulation.support.http.client.CqlQuery.lessThan;
import static org.folio.circulation.support.http.client.CqlQuery.notEqual;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.CqlSortClause;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CqlQueryTests {
  @Test
  void queryIsUrlEncoded() {
    final Result<CqlQuery> query = exactMatch("barcode", "  12345  ")
      .map(q -> q.sortBy(ascending("barcode")));

    final Result<String> encodedQueryResult = query.next(CqlQuery::encode);

    assertThat(encodedQueryResult.value(),
      is("barcode%3D%3D%22++12345++%22+sortBy+barcode%2Fsort.ascending"));
  }

  @Test
  void canExactlyMatchSingleValue() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    assertThat(query.value().asText(), is("barcode==\"12345\""));
  }

  @Test
  void canExactlyMatchAnyOfMultipleValues() {
    final Result<CqlQuery> query = exactMatchAny("barcode",
      asList("12345", "67890"));

    assertThat(query.value().asText(), is("barcode==(\"12345\" or \"67890\")"));
  }

  @Test
  void cannotExactlyMatchNoValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode",
      new ArrayList<>());

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  void cannotExactlyMatchNullValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode",
      asList(null, null));

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  void canApplyAndOperatorToTwoQueries() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    final Result<CqlQuery> secondQuery = exactMatch("status", "Open");

    final Result<CqlQuery> combinedQuery = query.combine(secondQuery,
      CqlQuery::and);

    assertThat(combinedQuery.value().asText(),
      is("barcode==\"12345\" and status==\"Open\""));
  }

  @Test
  void canSortQuery() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345")
      .map(q -> q.sortBy(ascending("position")));

    assertThat(query.value().asText(),
      is("barcode==\"12345\" sortBy position/sort.ascending"));
  }

  @Test
  void canApplyLessThenOperator() {
    ZonedDateTime dateTime = getZonedDateTime();
    Result<CqlQuery> query = lessThan("nextRunTime", dateTime);

    assertThat(query.value().asText(), is(format("nextRunTime<\"%s\"", dateTime)));
  }

  @Test
  void canApplyGreaterThenOperator() {
    ZonedDateTime dateTime = getZonedDateTime();

    Result<CqlQuery> query = greaterThan("lastTime", dateTime);

    assertThat(query.value().asText(), is(format("lastTime>\"%s\"", dateTime)));
  }

  @Test
  void canApplyNotEqualOperator() {
    ZonedDateTime dateTime = getZonedDateTime();
    Result<CqlQuery> query = notEqual("lastTime", dateTime);

    assertThat(query.value().asText(), is(format("lastTime<>\"%s\"", dateTime)));
  }

  @Test
  void canSortByMultipleIndexes() {
    Result<CqlQuery> query = exactMatch("barcode", "12345")
      .map(cqlQuery -> cqlQuery.sortBy(CqlSortBy.sortBy(
        CqlSortClause.ascending("position"),
        CqlSortClause.descending("lastTime"))));

    assertThat(query.value().asText(), is("barcode==\"12345\" " +
      "sortBy position/sort.ascending lastTime/sort.descending"));
  }

  @Test
  void shouldNotBeEqualToAnotherObject() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(5), is(false));
  }

  @Test
  void shouldNotBeEqualToNull() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(null), is(false));
  }

  @Test
  void shouldBeEqualToItself() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(query), is(true));
  }

  @Test
  void shouldBeEqualToQueryWithSameDefinition() {
    final CqlQuery firstQuery = exactMatch("barcode", "12345").value();
    final CqlQuery secondQuery = exactMatch("barcode", "12345").value();

    assertThat(firstQuery.equals(secondQuery), is(true));
  }

  @ParameterizedTest
  @CsvSource(nullValues={"null"}, value = {
    "index1, value1, index2, value2, (index1==\"value1\" or index2==\"value2\")",
    "null, null, index2, value2, (index2==\"value2\")",
    "index1, value1, null, null, (index1==\"value1\")",
  })
  void canMatchByAnyIndex(String index1, String value1, String index2, String value2,
    String expectedResult) {

    Map<String, String> filters = new HashMap<>();
    filters.put(index1, value1);
    filters.put(index2, value2);

    String actualResult = exactMatchAny(filters)
      .value()
      .asText();
    assertThat(actualResult, equalTo(expectedResult));
  }

  @Test
  void exactMatchAnyFailsWhenListOfIndicesIsEmpty() {
    Result<CqlQuery> result = exactMatchAny(emptyMap());
    assertThat("Failed result expected", result.failed());
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
    assertThat(((ServerErrorFailure) result.cause()).getReason(),
      equalTo("Cannot generate empty CQL query"));
  }
}
