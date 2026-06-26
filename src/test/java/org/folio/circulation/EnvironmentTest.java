package org.folio.circulation;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

  private static final String HTTP_MAXPOOLSIZE_ENV_VARIABLE = "HTTP_MAXPOOLSIZE";
  private static final String ECS_TLR_FEATURE_ENABLED_ENV_VARIABLE = "ECS_TLR_FEATURE_ENABLED";
  private static final int HTTP_MAXPOOLSIZE_DEFAULT = 100;

  @AfterEach
  void tearDownSystemVariable() {
    Environment.MOCK_ENV.remove(HTTP_MAXPOOLSIZE_ENV_VARIABLE);
    Environment.MOCK_ENV.remove(ECS_TLR_FEATURE_ENABLED_ENV_VARIABLE);
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

  @Test
  void testGetEcsTlrFeatureEnabledVarHasValidValue() {
    Environment.MOCK_ENV.put(ECS_TLR_FEATURE_ENABLED_ENV_VARIABLE, "true");
    assertThat(Environment.getEcsTlrFeatureEnabled(), is(true));
  }

  @Test
  void testGetEcsTlrFeatureEnabledVarHasInvalidValue() {
    Environment.MOCK_ENV.put(ECS_TLR_FEATURE_ENABLED_ENV_VARIABLE, "untrue");
    assertThat(Environment.getEcsTlrFeatureEnabled(), is(false));
  }

  @Test
  void testGetEcsTlrFeatureEnabledVarIsNotSet() {
    assertThat(Environment.getEcsTlrFeatureEnabled(), is(false));
  }
}
