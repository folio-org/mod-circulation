package org.folio.circulation.support.utils;

import java.util.concurrent.ThreadLocalRandom;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RandomUtil {

  public static String generateRandomDigits(int length) {
    StringBuilder builder = new StringBuilder(length);
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < length; i++) {
      builder.append(random.nextInt(10)); // 0–9
    }

    return builder.toString();
  }

}
