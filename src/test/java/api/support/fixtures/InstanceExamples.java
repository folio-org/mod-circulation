package api.support.fixtures;

import java.util.UUID;

import api.support.builders.InstanceBuilder;

public class InstanceExamples {
  public static InstanceBuilder basedUponSmallAngryPlanet(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId) {

    return new InstanceBuilder("The Long Way to a Small, Angry Planet",
      booksInstanceTypeId)
      .withContributor("Chambers, Becky", personalContributorNameTypeId);
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
  
  static InstanceBuilder basedUponOneOfHunderBookInstances(
    UUID booksInstanceTypeId,
    UUID personalContributorNameTypeId,
    int index) {
    if (index > books.length) {
      throw new RuntimeException("Only 100 book instances available indexed from 0-99");
    }
    String[] titleAndAuthor = books[index];
    return new InstanceBuilder(titleAndAuthor[0], booksInstanceTypeId)
      .withContributor(titleAndAuthor[1], personalContributorNameTypeId);
  }

  static String[][] books = new String[][] {
    new String[] { "The Pilgrim’s Progress", "John Bunyan" },
    new String[] { "Robinson Crusoe", "Daniel Defoe" },
    new String[] { "Gulliver’s Travels", "Jonathan Swift" },
    new String[] { "Clarissa", "Samuel Richardson" },
    new String[] { "Tom Jones", "Henry Fielding" },
    new String[] { "The Life and Opinions of Tristram Shandy, Gentleman", "Laurence Sterne" },
    new String[] { "Emma", "Jane Austen" },
    new String[] { "Frankenstein", "Mary Shelley" },
    new String[] { "Nightmare Abbey", "Thomas Love Peacock" },
    new String[] { "The Narrative of Arthur Gordon Pym of Nantucket", "Edgar Allan Poe" },
    new String[] { "Sybil", "Benjamin Disraeli" },
    new String[] { "Jane Eyre", "Charlotte Brontë" },
    new String[] { "Wuthering Heights", "Emily Brontë" },
    new String[] { "Vanity Fair", "William Thackeray" },
    new String[] { "David Copperfield", "Charles Dickens" },
    new String[] { "The Scarlet Letter", "Nathaniel Hawthorne" },
    new String[] { "Moby-Dick", "Herman Melville" },
    new String[] { "Alice’s Adventures in Wonderland", "Lewis Carroll" },
    new String[] { "The Moonstone", "Wilkie Collins" },
    new String[] { "Little Women", "Louisa May Alcott" },
    new String[] { "Middlemarch", "George Eliot" },
    new String[] { "The Way We Live Now", "Anthony Trollope" },
    new String[] { "The Adventures of Huckleberry Finn", "Mark Twain" },
    new String[] { "Kidnapped", "Robert Louis Stevenson" },
    new String[] { "Three Men in a Boat", "Jerome K Jerome" },
    new String[] { "The Sign of Four", "Arthur Conan Doyle" },
    new String[] { "The Picture of Dorian Gray", "Oscar Wilde" },
    new String[] { "New Grub Street", "George Gissing" },
    new String[] { "Jude the Obscure", "Thomas Hardy" },
    new String[] { "The Red Badge of Courage", "Stephen Crane" },
    new String[] { "Dracula", "Bram Stoker" },
    new String[] { "Heart of Darkness", "Joseph Conrad" },
    new String[] { "Sister Carrie", "Theodore Dreiser" },
    new String[] { "Kim", "Rudyard Kipling" },
    new String[] { "The Call of the Wild", "Jack London" },
    new String[] { "The Golden Bowl", "Henry James" },
    new String[] { "Hadrian the Seventh", "Frederick Rolfe" },
    new String[] { "The Wind in the Willows", "Kenneth Grahame" },
    new String[] { "The History of Mr Polly", "HG Wells" },
    new String[] { "Zuleika Dobson", "Max Beerbohm" },
    new String[] { "The Good Soldier", "Ford Madox Ford" },
    new String[] { "The Thirty-Nine Steps", "John Buchan" },
    new String[] { "The Rainbow", "DH Lawrence" },
    new String[] { "Of Human Bondage", "W Somerset Maugham" },
    new String[] { "The Age of Innocence", "Edith Wharton" },
    new String[] { "Ulysses", "James Joyce" },
    new String[] { "Babbitt", "Sinclair Lewis" },
    new String[] { "A Passage to India", "EM Forster" },
    new String[] { "Gentlemen Prefer Blondes", "Anita Loos" },
    new String[] { "Mrs Dalloway", "Virginia Woolf" },
    new String[] { "The Great Gatsby", "F Scott Fitzgerald" },
    new String[] { "Lolly Willowes", "Sylvia Townsend Warner" },
    new String[] { "The Sun Also Rises", "Ernest Hemingway" },
    new String[] { "The Maltese Falcon", "Dashiell Hammett" },
    new String[] { "As I Lay Dying", "William Faulkner" },
    new String[] { "Brave New World", "Aldous Huxley" },
    new String[] { "Cold Comfort Farm", "Stella Gibbons" },
    new String[] { "Nineteen Nineteen", "John Dos Passos" },
    new String[] { "Tropic of Cancer", "Henry Miller" },
    new String[] { "Scoop", "Evelyn Waugh" },
    new String[] { "Murphy", "Samuel Beckett" },
    new String[] { "The Big Sleep", "Raymond Chandler" },
    new String[] { "Party Going", "Henry Green" },
    new String[] { "At Swim-Two-Birds", "Flann O’Brien" },
    new String[] { "The Grapes of Wrath", "John Steinbeck" },
    new String[] { "Joy in the Morning", "PG Wodehouse" },
    new String[] { "All the King’s Men", "Robert Penn Warren" },
    new String[] { "Under the Volcano", "Malcolm Lowry" },
    new String[] { "The Heat of the Day", "Elizabeth Bowen" },
    new String[] { "Nineteen Eighty-Four", "George Orwell" },
    new String[] { "The End of the Affair", "Graham Greene" },
    new String[] { "The Catcher in the Rye", "JD Salinger" },
    new String[] { "The Adventures of Augie March", "Saul Bellow" },
    new String[] { "Lord of the Flies", "William Golding" },
    new String[] { "Lolita", "Vladimir Nabokov" },
    new String[] { "On the Road", "Jack Kerouac" },
    new String[] { "Voss", "Patrick White" }, 
    new String[] { "To Kill a Mockingbird", "Harper Lee" },
    new String[] { "The Prime of Miss Jean Brodie", "Muriel Spark" },
    new String[] { "Catch-22", "Joseph Heller" },
    new String[] { "The Golden Notebook", "Doris Lessing" },
    new String[] { "A Clockwork Orange", "Anthony Burgess" },
    new String[] { "A Single Man", "Christopher Isherwood" },
    new String[] { "In Cold Blood", "Truman Capote" },
    new String[] { "The Bell Jar", "Sylvia Plath" },
    new String[] { "Portnoy’s Complaint", "Philip Roth" },
    new String[] { "Mrs Palfrey at the Claremont", "Elizabeth Taylor" },
    new String[] { "Rabbit Redux", "John Updike" },
    new String[] { "Song of Solomon", "Toni Morrison" },
    new String[] { "A Bend in the River", "VS Naipaul" },
    new String[] { "Midnight’s Children", "Salman Rushdie" },
    new String[] { "Housekeeping", "Marilynne Robinson" },
    new String[] { "Money: A Suicide Note", "Martin Amis" },
    new String[] { "An Artist of the Floating World", "Kazuo Ishiguro" },
    new String[] { "The Beginning of Spring", "Penelope Fitzgerald" },
    new String[] { "Breathing Lessons", "Anne Tyler" },
    new String[] { "Amongst Women", "John McGahern" },
    new String[] { "Underworld", "Don DeLillo" },
    new String[] { "Disgrace", "JM Coetzee" },
    new String[] { "True History of the Kelly Gang", "Peter Carey" },
  };
}
