package org.folio.circulation;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class EnvironmentTest {

  private static final String HTTP_MAXPOOLSIZE_ENV_VARIABLE = "HTTP_MAXPOOLSIZE";
  private static final int HTTP_MAXPOOLSIZE_DEFAULT = 100;

  @AfterEach
  void tearDownSystemVariable() {
    Environment.MOCK_ENV.remove(HTTP_MAXPOOLSIZE_ENV_VARIABLE);
  }

  @Test
  void testDefaultMaxPoolSizeWhenEnvVarMissing() {
    assertThat(Environment.getHttpMaxPoolSize(), is(HTTP_MAXPOOLSIZE_DEFAULT));
  }

  @Test
  void testMaxPoolSizeWhenEnvVarIsValid() {
    Environment.MOCK_ENV.put(HTTP_MAXPOOLSIZE_ENV_VARIABLE, "123");
    assertThat(Environment.getHttpMaxPoolSize(), is(123));
  }

  @Test
  void testFallbackToDefaultWhenEnvVarIsInvalid() {
    Environment.MOCK_ENV.put(HTTP_MAXPOOLSIZE_ENV_VARIABLE, "invalid");
    assertThat(Environment.getHttpMaxPoolSize(), is(HTTP_MAXPOOLSIZE_DEFAULT));
  }
}
