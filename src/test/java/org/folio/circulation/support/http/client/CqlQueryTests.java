package org.folio.circulation.support.http.client;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.CqlQuery.greaterThan;
import static org.folio.circulation.support.http.client.CqlQuery.lessThan;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;

import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.CqlSortClause;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;
import org.junit.Test;

public class CqlQueryTests {
  @Test
  public void queryIsUrlEncoded() {
    final Result<CqlQuery> query = exactMatch("barcode", "  12345  ")
      .map(q -> q.sortBy(ascending("barcode")));

    final Result<String> encodedQueryResult = query.next(CqlQuery::encode);

    assertThat(encodedQueryResult.value(),
      is("barcode%3D%3D%22++12345++%22+sortBy+barcode%2Fsort.ascending"));
  }

  @Test
  public void canExactlyMatchSingleValue() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    assertThat(query.value().asText(), is("barcode==\"12345\""));
  }

  @Test
  public void canExactlyMatchAnyOfMultipleValues() {
    final Result<CqlQuery> query = exactMatchAny("barcode",
      asList("12345", "67890"));

    assertThat(query.value().asText(), is("barcode==(\"12345\" or \"67890\")"));
  }

  @Test
  public void cannotExactlyMatchNoValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode",
      new ArrayList<>());

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  public void cannotExactlyMatchNullValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode",
      asList(null, null));

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  public void canApplyAndOperatorToTwoQueries() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    final Result<CqlQuery> secondQuery = exactMatch("status", "Open");

    final Result<CqlQuery> combinedQuery = query.combine(secondQuery,
      CqlQuery::and);

    assertThat(combinedQuery.value().asText(),
      is("barcode==\"12345\" and status==\"Open\""));
  }

  @Test
  public void canSortQuery() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345")
      .map(q -> q.sortBy(ascending("position")));

    assertThat(query.value().asText(),
      is("barcode==\"12345\" sortBy position/sort.ascending"));
  }

  @Test
  public void canApplyLessThenOperator() {
    DateTime dateTime = now(UTC);
    Result<CqlQuery> query = lessThan("nextRunTime", dateTime);

    assertThat(query.value().asText(), is(format("nextRunTime<\"%s\"", dateTime)));
  }

  @Test
  public void canApplyGreaterThenOperator() {
    DateTime dateTime = now(UTC);

    Result<CqlQuery> query = greaterThan("lastTime", dateTime);

    assertThat(query.value().asText(), is(format("lastTime>\"%s\"", dateTime)));
  }

  @Test
  public void canSortByMultipleIndexes() {
    Result<CqlQuery> query = exactMatch("barcode", "12345")
      .map(cqlQuery -> cqlQuery.sortBy(CqlSortBy.sortBy(
        CqlSortClause.ascending("position"),
        CqlSortClause.descending("lastTime"))));

    assertThat(query.value().asText(), is("barcode==\"12345\" " +
      "sortBy position/sort.ascending lastTime/sort.descending"));
  }
}
