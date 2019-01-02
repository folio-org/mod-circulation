package api.support.fixtures;

import java.util.UUID;

import api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet(
    UUID booksInstanceTypeId) {

    return new InstanceBuilder("The Long Way to a Small, Angry Planet", booksInstanceTypeId)
      .withContributor("Chambers, Becky");
  }

  public static InstanceBuilder basedUponNod(UUID booksInstanceTypeId) {
    return new InstanceBuilder("Nod", booksInstanceTypeId)
      .withContributor("Barnes, Adrian");
  }

  static InstanceBuilder basedUponUprooted(UUID booksInstanceTypeId) {
    return new InstanceBuilder("Uprooted", booksInstanceTypeId)
      .withContributor("Novik, Naomi");
  }

  public static InstanceBuilder basedUponTemeraire(UUID booksInstanceTypeId) {
    return new InstanceBuilder("Temeraire", booksInstanceTypeId)
      .withContributor("Novik, Naomi");
  }

  static InstanceBuilder basedUponInterestingTimes(UUID booksInstanceTypeId) {
    return new InstanceBuilder("Interesting Times", booksInstanceTypeId)
      .withContributor("Pratchett, Terry");
  }

  static InstanceBuilder basedUponDunkirk(UUID booksInstanceTypeId) {
    return new InstanceBuilder("Dunkirk", booksInstanceTypeId)
      .withContributor("Nolan, Christopher");
  }
}
