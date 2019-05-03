package org.folio.circulation.support;

import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import org.junit.Test;

public class CqlQueryTests {
  @Test
  public void queryIsUrlEncoded() {
    final String queryWithSpaces = "barcode==\"  12345  \"";

    final Result<String> encodedQueryResult = new CqlQuery(queryWithSpaces).encode();

    assertThat(encodedQueryResult.value(), is("barcode%3D%3D%22++12345++%22"));
  }

  @Test
  public void canExactlyMatchSingleValue() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    assertThat(query.value().asText(), is("barcode==\"12345\""));
  }

  @Test
  public void canExactlyMatchAnyOfMultipleValues() {
    final Result<CqlQuery> query = exactMatchAny("barcode",
      new ArrayList<String>() {
        {
          add("12345");
          add("67890");
        }
    });

    assertThat(query.value().asText(), is("barcode==(12345 or 67890)"));
  }

  @Test
  public void cannotExactlyMatchNoValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode", new ArrayList<>());

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  public void cannotExactlyMatchNullValues() {
    final Result<CqlQuery> queryResult = exactMatchAny("barcode",
      new ArrayList<String>() {
      {
        add(null);
        add(null);
      }
    });

    assertThat(queryResult.failed(), is(true));
    assertThat(queryResult.cause(), is(instanceOf(ServerErrorFailure.class)));
  }

  @Test
  public void canApplyAndOperatorToTwoQueries() {
    final Result<CqlQuery> query = exactMatch("barcode", "12345");

    final Result<CqlQuery> secondQuery = exactMatch("status", "Open");

    final Result<CqlQuery> combinedQuery = query.combine(secondQuery, CqlQuery::and);

    assertThat(combinedQuery.value().asText(),
      is("barcode==\"12345\" and status==\"Open\""));
  }
}
