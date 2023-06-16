# Domain Modeling Made Functional in Kotlin + Arrow

## Overview

["Domain Modeling Made Functional"](https://pragprog.com/titles/swdddf/domain-modeling-made-functional/) is a highly appraised book
that introduces core concepts of Domain Driven Design and practical domain modeling techniques by using functional programming.
The book only uses basic functional programming techniques without stepping into "scary-sounding" concepts such as monads.
The book can be purchased via [the author's website](https://fsharpforfunandprofit.com/books/).

I created this repository for my personal study to understand

- how well the techniques explained in the book can be written in Kotlin and [arrow-kt](https://arrow-kt.io/)
  (e.g. immutability, algebraic data types, pure functions, function compositions),
- when to use functional programming over object oriented programming
- if it's easy enough for developers to learn and practice the methodologies without prior knowledge

The original F# code is in this repository: https://github.com/swlaschin/DomainModelingMadeFunctional

## Summary of the code changes

### 1. Copied code from the repository

The original code: [OrderTaking](https://github.com/swlaschin/DomainModelingMadeFunctional/tree/master/src/OrderTaking). Reordered some modules for taking a diff: [PR #2](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/2/files)

### 2. Migrated the F# code to Kotlin + Arrow

Manually migrated the F# code by using Kotlin and Arrow: [PR #3](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/3/files)

Most of the code conversions were straight-forward. Examples:

| before                     | after                                 |
|----------------------------|---------------------------------------|
| simple type                | value class                           |
| product type               | data class                            |
| sum type                   | enum class or sealed interface        |
| module for a specific type | companion object, extension functions |
| `Result`                   | `Either`                              |
| `Async`                    | `suspend`                             |
| `T list`                   | `List<T>`                             |
| `T option`                 | `T?`                                  |
| `\|>`                      | `map` (if available) or `let`         |
| partial application        | `partially1`                          |

### 3. Added more changes

Added the changes explained in the chapter 13 in the book: [PR #4](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/4/files)

The original code: [OrderTakingEvolved](https://github.com/swlaschin/DomainModelingMadeFunctional/tree/master/src/OrderTakingEvolved)

### 4. Refactoring

Added these changes to make it more readable as Kotlin code: [PR #5](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5)

- Replace unnecessary partial applications with simple function invocations (each partially1 call creates a new anonymous Function instance). [Example](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-aba9e47720673ddd68ff904d256f10e2125e379a98b6a5d46a5e68b1c55c275fR237)
- Use Arrow DSL to make it more readable (either / nullable). [Example](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-b287e874f07c94484182b17ad30c3e772e9a3f66d06793900dba5073220b5768R283)
- Moved some values into instances from functions (e.g. [text](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-b287e874f07c94484182b17ad30c3e772e9a3f66d06793900dba5073220b5768R47), [quantity](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-b287e874f07c94484182b17ad30c3e772e9a3f66d06793900dba5073220b5768R198), [cost](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-aba9e47720673ddd68ff904d256f10e2125e379a98b6a5d46a5e68b1c55c275fR407))
- Use `operator fun invoke(...)` for factory methods in companion objects

### 5. Context Receivers for dependency injections

Tried using Kotlin's [Context Receivers](https://github.com/Kotlin/KEEP/blob/master/proposals/context-receivers.md) for dependency injections: [PR #6](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/6/files#diff-aba9e47720673ddd68ff904d256f10e2125e379a98b6a5d46a5e68b1c55c275fR562)

## Consideration

Many development teams are hesitant to adopt functional programming due to its steep learning curve.
In reality, many beginners struggle with understanding functional programming concepts, design patterns, and underlying theories.

The concrete methodologies presented in this book would be simple enough for such people to accept.
Additionally, the combination of Kotlin, which offers practical functionalities with rich tooling support,
along with Arrow, which provides features for functional programming without overwhelming users with abstract concepts,
would be a great choice for these teams.

### Kotlin

Most of the code migrations were straightforward and the techniques explained in the book can be easily applied in Kotlin.

- Data modeling: `val` (immutable), `T?` (optional), immutable collections, value class, data class, enum class, sealed interface
- Side effects: `suspend`
- Module: companion object
- Dependency injection: extension functions, context receivers

### Arrow

In this repository, I only used basic features (`Either`, `either` / `nullable` DSLs).
It would be easy enough for beginners without functional programming experience to learn these as implementation patterns.
(It was harder to use `Raise<E>` at this point.)

I also tried [partially1 in Arrow](https://arrow-kt.io/learn/collections-functions/utils/#partial-application) for the initial version,
but it would be more straightforward to use
Kotlin's built-in features ([lambdas](https://kotlinlang.org/docs/lambdas.html), [local functions](https://kotlinlang.org/docs/functions.html#local-functions))
or simple function invocations in most cases because

- the use of `partiallyN` makes it harder to understand (verbose and not idiomatic)
- each `partiallyN` call creates a new anonymous `FunctionN` instance at runtime
- tooling support (e.g. compiler, IDEs) is limited

Similarly, relying solely on functions might not be reasonable when writing code in Kotlin (Scala would be a better choice in that case)
due to readability, performance, ecosystem, and tooling support.
In general, it would be more advantageous to leverage features offered by the language's ecosystem rather than adhering to the functional programming paradigm.
Examples: [text](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-b287e874f07c94484182b17ad30c3e772e9a3f66d06793900dba5073220b5768R47), [quantity](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-b287e874f07c94484182b17ad30c3e772e9a3f66d06793900dba5073220b5768R198), [cost](https://github.com/uharaqo/domain-modeling-made-functional-kotlin/pull/5/files#diff-aba9e47720673ddd68ff904d256f10e2125e379a98b6a5d46a5e68b1c55c275fR407).
