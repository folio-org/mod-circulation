package org.folio.circulation.support.utils;

import java.security.SecureRandom;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomUtil {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public static String generateRandomDigits(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append(SECURE_RANDOM.nextInt(10)); // 0–9
    }
    return builder.toString();
  }

}
