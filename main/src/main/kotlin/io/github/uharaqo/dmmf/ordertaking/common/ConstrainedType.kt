package io.github.uharaqo.dmmf.ordertaking.common

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.nullable

// ===============================
// Reusable constructors and getters for constrained types
// ===============================

// Useful functions for constrained types
object ConstrainedType {

    // Create a constrained string using the constructor provided
    // Return Error if input is null, empty, or length > maxLen
    fun <T> createString(fieldName: String, ctor: (String) -> T, maxLen: Int, str: String?): Either<String, T> =
        either {
            ensureNotNull(str) { "$fieldName must not be null" }
            ensure(str.isNotEmpty()) { "$fieldName must not be empty" }
            ensure(str.length <= maxLen) { "$fieldName must not be more than $maxLen chars" }
            ctor(str)
        }

    // Create a optional constrained string using the constructor provided
    // Return None if input is null, empty.
    // Return error if length > maxLen
    // Return Some if the input is valid
    fun <T> createStringOption(fieldName: String, ctor: (String) -> T, maxLen: Int, str: String?): Either<String, T?> =
        either {
            nullable {
                if (str == null) raise(null)
                this@either.ensure(str.length <= maxLen) { "$fieldName must not be more than $maxLen chars" }
                ctor(str)
            }
        }

    // Create a constrained integer using the constructor provided
    // Return Error if input is less than minVal or more than maxVal
    fun <T> createInt(fieldName: String, ctor: (Int) -> T, minVal: Int, maxVal: Int, i: Int): Either<String, T> =
        either {
            ensure(i >= minVal) { "$fieldName: Must not be less than $minVal" }
            ensure(i <= maxVal) { "$fieldName: Must not be greater than $maxVal" }
            ctor(i)
        }

    // Create a constrained decimal using the constructor provided
    // Return Error if input is less than minVal or more than maxVal
    fun <T> createDecimal(
        fieldName: String,
        ctor: (Double) -> T,
        minVal: Double,
        maxVal: Double,
        i: Double,
    ): Either<String, T> =
        either {
            ensure(i >= minVal) { "$fieldName: Must not be less than $minVal" }
            ensure(i <= maxVal) { "$fieldName: Must not be greater than $maxVal" }
            ctor(i)
        }

    // Create a constrained string using the constructor provided
    // Return Error if input is null. empty, or does not match the regex pattern
    fun <T> createLike(fieldName: String, ctor: (String) -> T, pattern: Regex, str: String?): Either<String, T> =
        either {
            ensureNotNull(str) { "$fieldName: Must not be null" }
            ensure(str.isNotEmpty()) { "$fieldName: Must not be empty" }
            ensure(pattern.matches(str)) { "$fieldName: '$str' must match the pattern '$pattern'" }
            ctor(str)
        }
}
