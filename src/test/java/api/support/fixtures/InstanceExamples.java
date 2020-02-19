package api.support.fixtures;

import java.util.UUID;

import api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("The Long Way to a Small, Angry Planet",
      booksInstanceTypeId)
      .withContributor("Chambers, Becky", personalContributorNameTypeId, true);
  }

  public static InstanceBuilder basedUponNod(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("Nod", booksInstanceTypeId)
      .withContributor("Barnes, Adrian", personalContributorNameTypeId);
  }

  static InstanceBuilder basedUponUprooted(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("Uprooted", booksInstanceTypeId)
      .withContributor("Novik, Naomi", personalContributorNameTypeId);
  }

  public static InstanceBuilder basedUponTemeraire(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("Temeraire", booksInstanceTypeId)
      .withContributor("Novik, Naomi", personalContributorNameTypeId);
  }

  static InstanceBuilder basedUponInterestingTimes(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {
    
    return new InstanceBuilder("Interesting Times", booksInstanceTypeId)
      .withContributor("Pratchett, Terry", personalContributorNameTypeId);
  }

  static InstanceBuilder basedUponDunkirk(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("Dunkirk", booksInstanceTypeId)
      .withContributor("Nolan, Christopher", personalContributorNameTypeId);
  }
}
