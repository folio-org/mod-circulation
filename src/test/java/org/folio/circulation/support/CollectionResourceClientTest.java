package org.folio.circulation.support;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CollectionResourceClientTest {
  static private String createQueryString(String urlencodedCqlQuery, Integer pageLimit, Integer pageOffset) {
    return CollectionResourceClient.createQueryString(urlencodedCqlQuery, pageLimit, pageOffset);
  }

  @Test
  public void createQueryString() {
    assertThat(createQueryString("field%3Da", 5,    10  ), is("?query=field%3Da&limit=5&offset=10"));
    assertThat(createQueryString("a%20b",     null, null), is("?query=a%20b"));
    assertThat(createQueryString(null,        6,    null), is("?limit=6"));
    assertThat(createQueryString(null,        null, 11  ), is("?offset=11"));
    assertThat(createQueryString(null,        null, null), is(""));
  }
}
