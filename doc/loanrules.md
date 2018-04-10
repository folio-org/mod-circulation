# Loan Rules

The loan rules engine calculates the loan policy (that specifies the loan period)
based on the patron's patron group and the item's material type, loan type, and location.

Example loan rules file:

    fallback-policy: no-circulation
    m book : regular-loan
    m newspaper: reading-room
    m streaming-subscription: policy-s
        g visitor undergrad: in-house

How does this short example work?

The default is the `no-circulation` loan policy. It is taken if no other rule matches.

After `m` is the item's material type. Books can be loaned using the `regular-loan` loan policy,
newspapers can be loaned for the `reading-room` only.

Streaming subscriptions use the `policy-s` loan policy; however there are two
exceptions placed in the next line using indentation: For the user groups (`g`)
`visitor` and `undergrad` the streaming subscriptions can be loaned using
the `in-house` loan policy only.

## Loan Policy

The name of a loan policy is appended to a criteria line separated by a colon.
If the line matches then that policy is applied.

The loan policy is optional.

## Criteria type names

These are the single letter criteria type names:

* `g` the patron's patron group (like staff, undergrad, visitor)
* `m` the item's material type (like book, newspaper)
* `t` the item's loan type (like rare, course-reserve)
* `a` the item's campus (location)
* `b` the item's branch (location)
* `c` the item's collection (location)
* `s` the item's shelf (location)

`a`, `b`, `c` and `s` build a location hierarchy; `a`, `b` and `c` are not
implemented for FOLIO version 1, only `s`.

## Criterium

A criterium consists of a single letter criterium type and a name selection of that type.

If it has one or more names of that type like `g visitor undergrad` then it matches
any patron group listed.

If it has one or more negated names of that type like `g !visitor !undergrad` then it matches
any patron group that is not listed, for example `staff`.

Use the keyword `all` for the name selection like `g all` to match all patron groups.
This is needed to alter the rule priority, see below.

## Criteria

Criteria can be combined in two ways:

Concatenating them into one line using `+` as separator like `g visitor + t rare: policy-a`
matches if the patron group is `visitor` and the loan type is `rare`.

To avoid long lines one may replace the `+` by a line break followed by an indentation like

```
g visitor
    t rare: policy-a
```

The second line matches when the criteria of the first and the second line matches.

Indentation allows for a nested hierarchy:

```
g staff: policy-a
g visitor: policy-b
    m book: policy-c
        t rare: policy-d
        t course-reserve: policy-e
            s law-department: policy-f
            s math-department: policy-g
    s new-acquisition: policy-h
```

A current line matches if the current line's criteria matches and for
each smaller indentation level the last line before the current line
has matching criteria.

The hierarchy shown before contains these rules:

Patron group staff (indentation level 0) gives policy-a. (This is true for any material type,
any loan type and any shelving location.)

Patron group visitor (indentation level 0) and shelving location new-acquisition
(indentation level 1) gives policy-h. (This is true for any loan type and any material type.)

Patron group visitor (indentation level 0) and material type book (indentation level 1)
and loan type course-reserve (indentation level 2) and shelving location math-department
(indentation level 3) gives policy-g.

Patron group visitor (indentation level 0) and material type book (indentation level 1)
and loan type course-reserve (indentation level 2) and shelving location law-department
(indentation level 3) gives policy-f.

Patron group visitor (indentation level 0) and material type book (indentation level 1)
and loan type course-reserve (indentation level 2) and any shelving location different from
math-department and law-department gives policy-e.

Patron group visitor (indentation level 0) and material type book (indentation level 1)
and loan type rare (indentation level 2) gives policy-d. (This is true for any shelving location.)

Patron group visitor (indentation level 0) and material type book (indentation level 1)
and any loan type different from rare and course-reserve gives policy-c.
(This is true for any shelving location.)

Patron group visitor (indentation level 0) and any material type different from book and globe
gives policy-b. (This is true for any loan type and any shelving location.)

## Multiple matching rules

If more than one rule matches then the rule with the highest priority is used. The priority line
lists the priority regulations in the order they are checked until only a single matching rule
remains. The priority line must be before the first rule line.

The priority line may contain one, two or three priority regulations. The last regulation must be
one of the two line regulations `first-line` and `last-line`.

Before the line regulation there can be zero, one or two of the other regulations:
`criterium (…)`, `number-of-criteria`

The `criterium (…)` regulation contains the seven criterium types in any order, for example
`criterium (t, s, c, b, a, m, g)`.

This is an example for a complete priority line:

`policy: number-of-criteria, criterium (t, s, c, b, a, m, g), last-line`

For compatibility with former versions a priority line may contain only the seven criterium types:

`priority: t, s, c, b, a, m, g`

This is the same as

`priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line`

### Criterium type priority

The criterium priority lists the criterium types in decreasing priority, for example
`criterium(t, s, c, b, a, m, g)`. For each rule take the criterium type with
the highest priority. Now compare the matching rules using that criterium type.
The rules with the highest priority win.

Example a:

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor: policy-a
t rare: policy-c
m book: policy-e
```

A loan for patron group `visitor` and loan type `rare` and material type `book` matches
all three rules. However, the policy-c rule is the only rule with the highest criterium
type `t` and wins.

Example b:

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor: policy-a
    t rare: policy-b
t rare: policy-c
    m book: policy-d
m book: policy-e
```

We assign priority numbers to the criterium types:
t=7, a=6, b=5, c=4, s=3, m=2, g=1.

A loan for material type `book` and loan type `rare` and patron group `visitor` matches
all five rules and each rule has this priority:

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor: max(g=1)=1: policy-a
g visitor + t rare: max(g=1, t=7)=7: policy-b
t rare: max(t=7)=7: policy-c
t rare + m book: max(t=7, m=2)=7: policy-d
m book: max(m=2)=2: policy-e
```

The three rules with policy-b, policy-c and policy-d have the highest criterium type priority of 7.
For the tie we need to continue with "2. Rule specificity priority".

The rule with policy-e has a lower criterium type priority of 2,
the rule with policy-a has the lowest criterium type priority of 1.

### Rule specificity priority (number-of-criteria)

Specificity is the number of criterium types.  Higher number of criterium types has higher priority.
Any number of location criterium types (`a`, `b`, `c`, `s`) count as one.

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor + t rare: policy-b
t rare: policy-c
t rare + m book: policy-d
```

A loan for material type `book` and loan type `rare` and patron group `visitor` matches
all three rules. The criterium type priority is the same (`t`). The line with policy-b has a
specificity of 2 because it has two criteria (`g` and `t`). The line with policy-c has a
specificity of 1 because it has only one criterium (`t`). The line policy-d has a
specificity of 2 because it has two criteria (`t` and `m`).

The rules with policy-b and policy-d have the highest specificity priority of 2.

Use the `all` keyword to match all names of that criterium type. That way the
criterium type is used for both the Criterium type priority and the Rule specificity
priority but without restricting to some names. Example:

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor + t rare: policy-b
t rare: policy-c
t rare + m book: policy-d
g all + t all + s course-reserve: policy-e
```

The policy-e rule has priority over the other three rules because it has a `t` criterium
and uses three criteria.

### 3. Line number priority

For the line number priority the order of the rules is relevant. The `last-line` the
last matching rule (the rule with the highest line number) is taken, for `first-line` the
first matching rule (the rule with the lowest line numer) is taken.

```
priority: criterium(t, s, c, b, a, m, g), number-of-criteria, last-line
fallback-policy: no-circulation
g visitor + t rare: policy-b
t rare + m book: policy-d
```

A loan for material type `book` and loan type `rare` and patron group `visitor` matches
both lines. The criterium type priority is the same (`t`). They both have two criteria.
The line with policy-d has higher priority because it is last (it has a higher line number).

## Fallback policy

There always must be a line with a fallback policy like `fallback-policy: no-circulation`.
It must be after the priority line and before the the first rule.

For `priority: last-line` it must be after the last rule.
