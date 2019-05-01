package org.folio.circulation.support;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CqlQueryTests {
  @Test
  public void queryIsUrlEncoded() {
    final String queryWithSpaces = "barcode==\"  12345  \"";

    final Result<String> encodedQueryResult = new CqlQuery(queryWithSpaces).encode();

    assertThat(encodedQueryResult.value(), is("barcode%3D%3D%22++12345++%22"));
  }
}
