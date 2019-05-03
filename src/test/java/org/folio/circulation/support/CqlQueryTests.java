package org.folio.circulation.support;

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
    final CqlQuery query = CqlQuery.exactMatch("barcode", "12345");

    assertThat(query.asText(), is("barcode==\"12345\""));
  }

  //TODO: Add test for no values
  @Test
  public void canExactlyMatchAnyOfMultipleValues() {
    final CqlQuery query = CqlQuery.exactMatchAny("barcode",
      new ArrayList<String>() {
        {
          add("12345");
          add("67890");
        }
    });

    assertThat(query.asText(), is("barcode==(12345 or 67890)"));
  }
}
