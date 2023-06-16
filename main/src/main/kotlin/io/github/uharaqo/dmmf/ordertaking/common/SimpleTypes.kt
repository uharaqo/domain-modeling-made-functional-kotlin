package io.github.uharaqo.dmmf.ordertaking.common

import arrow.core.*
import arrow.core.raise.*

// We are defining types and submodules, so we can use a namespace
// rather than a module at the top level

// ===============================
// Simple types and constrained types related to the OrderTaking domain.
//
// E.g. Single case discriminated unions (aka wrappers), enums, etc
// ===============================

// Constrained to be 50 chars or less, not null
@JvmInline
value class String50(val value: String) {
    companion object {
        // Create an String50 from a string
        // Return Error if input is null, empty, or length > 50
        operator fun invoke(fieldName: String, str: String): Either<String, String50> =
            ConstrainedType.createString(fieldName, ::String50, 50, str)

        // Create an String50 from a string
        // Return None if input is null, empty.
        // Return error if length > maxLen
        // Return Some if the input is valid
        fun optional(fieldName: String, str: String): Either<String, String50?> =
            ConstrainedType.createStringOption(fieldName, ::String50, 50, str)
    }
}

// An email address
@JvmInline
value class EmailAddress(val value: String) {
    companion object {
        private val pattern = Regex(".+@.+")

        // Create an EmailAddress from a string
        // Return Error if input is null, empty, or doesn't have an "@" in it
        operator fun invoke(fieldName: String, str: String): Either<String, EmailAddress> =
            ConstrainedType.createLike(fieldName, ::EmailAddress, pattern, str) // anything separated by an "@"
    }
}

// Customer's VIP status
enum class VipStatus(val text: String) {
    Normal("Normal"),
    Vip("VIP"),
    ;

    companion object {
        private val lookup = values().associateBy { it.text }

        //  Create a VipStatus from a string
        //  Return Error if input is null, empty, or doesn't match one of the cases
        operator fun invoke(fieldName: String, str: String): Either<String, VipStatus> = either {
            ensureNotNull(lookup[str]) { "$fieldName: Must be one of 'Normal', 'VIP'" }
        }
    }
}

// A zip code
@JvmInline
value class ZipCode(val value: String) {
    companion object {
        private val pattern = Regex("""\d{5}""")

        // Create a ZipCode from a string
        // Return Error if input is null, empty, or doesn't have 5 digits
        operator fun invoke(fieldName: String, str: String): Either<String, ZipCode> =
            ConstrainedType.createLike(fieldName, ::ZipCode, pattern, str)
    }
}

// A US 2 letter state code
@JvmInline
value class UsStateCode(val value: String) {
    companion object {
        private val pattern =
            Regex("^(A[KLRZ]|C[AOT]|D[CE]|FL|GA|HI|I[ADLN]|K[SY]|LA|M[ADEINOST]|N[CDEHJMVY]|O[HKR]|P[AR]|RI|S[CD]|T[NX]|UT|V[AIT]|W[AIVY])$")

        //  Create a UsStateCode from a string
        //  Return Error if input is null, empty, or doesn't have 2 letters
        operator fun invoke(fieldName: String, str: String): Either<String, UsStateCode> =
            ConstrainedType.createLike(fieldName, ::UsStateCode, pattern, str)
    }
}

// An Id for Orders. Constrained to be a non-empty string < 10 chars
@JvmInline
value class OrderId(val value: String) {
    companion object {
        // Create an OrderId from a string
        // Return Error if input is null, empty, or length > 50
        operator fun invoke(fieldName: String, str: String): Either<String, OrderId> =
            ConstrainedType.createString(fieldName, ::OrderId, 50, str)
    }
}

// An Id for OrderLines. Constrained to be a non-empty string < 10 chars
@JvmInline
value class OrderLineId(val value: String) {

    companion object {
        // Create an OrderLineId from a string
        // Return Error if input is null, empty, or length > 50
        operator fun invoke(fieldName: String, str: String): Either<String, OrderLineId> =
            ConstrainedType.createString(fieldName, ::OrderLineId, 50, str)
    }
}

// The codes for Widgets start with a "W" and then four digits

@JvmInline
value class WidgetCode(val value: String) {
    companion object {
        private val pattern = Regex("""W\d{4}""")

        // Create an WidgetCode from a string
        // Return Error if input is null. empty, or not matching pattern
        operator fun invoke(fieldName: String, code: String): Either<String, WidgetCode> =
            // The codes for Widgets start with a "W" and then four digits
            ConstrainedType.createLike(fieldName, ::WidgetCode, pattern, code)
    }
}

// The codes for Gizmos start with a "G" and then three digits.
@JvmInline
value class GizmoCode(val value: String) {
    companion object {
        private val pattern = Regex("""G\d{3}""")

        // Create an GizmoCode from a string
        // Return Error if input is null, empty, or not matching pattern
        operator fun invoke(fieldName: String, code: String): Either<String, GizmoCode> =
            // The codes for Gizmos start with a "G" and then three digits.
            ConstrainedType.createLike(fieldName, ::GizmoCode, pattern, code)
    }
}

// A ProductCode is either a Widget or a Gizmo
sealed interface ProductCode {
    val text: String

    @JvmInline
    value class Widget(val value: WidgetCode) : ProductCode {
        override val text: String
            get() = value.value
    }

    @JvmInline
    value class Gizmo(val value: GizmoCode) : ProductCode {
        override val text: String
            get() = value.value
    }

    companion object {
        // Create an ProductCode from a string
        // Return Error if input is null, empty, or not matching pattern
        operator fun invoke(fieldName: String, code: String?): Either<String, ProductCode> =
            either {
                ensureNotNull(code) { "$fieldName: Must not be null" }
                ensure(code.isNotEmpty()) { "$fieldName: Must not be empty" }
                when {
                    code.startsWith("W") -> WidgetCode(fieldName, code).map(::Widget)
                    code.startsWith("G") -> GizmoCode(fieldName, code).map(::Gizmo)
                    else -> raise("$fieldName: Format not recognized '$code'")
                }.bind()
            }
    }
}

// Constrained to be a integer between 1 and 1000
@JvmInline
value class UnitQuantity(val value: Int) {
    companion object {
        // Create a UnitQuantity from a int
        // Return Error if input is not an integer between 1 and 1000
        operator fun invoke(fieldName: String, v: Int): Either<String, UnitQuantity> =
            ConstrainedType.createInt(fieldName, ::UnitQuantity, 1, 1000, v)
    }
}

// Constrained to be a decimal between 0.05 and 100.00
@JvmInline
value class KilogramQuantity(val value: Double) {
    companion object {
        // Create a KilogramQuantity from a decimal.
        // Return Error if input is not a decimal between 0.05 and 100.00
        operator fun invoke(fieldName: String, v: Double): Either<String, KilogramQuantity> =
            ConstrainedType.createDecimal(fieldName, ::KilogramQuantity, 0.05, 100.00, v)
    }
}

// Constrained to be a decimal between 0.0 and 1000.00
@JvmInline
value class Price(val value: Double) {
    companion object {
        // Create a Price from a decimal.
        // Return Error if input is not a decimal between 0.0 and 1000.00
        operator fun invoke(v: Double): Either<String, Price> =
            ConstrainedType.createDecimal("Price", ::Price, 0.0, 1000.00, v)

        // Create a Price from a decimal.
        // Throw an exception if out of bounds. This should only be used if you know the value is valid.
        fun unsafeCreate(v: Double): Price =
            invoke(v)
                .fold({ err -> throw RuntimeException("Not expecting Price to be out of bounds: $err") }, { it })

        // Multiply a Price by a decimal qty.
        // Return Error if new price is out of bounds.
        fun multiply(qty: Double, p: Price): Either<String, Price> =
            invoke(qty * p.value)
    }
}

// Constrained to be a decimal between 0.0 and 10000.00
@JvmInline
value class BillingAmount(val value: Double) {
    companion object {
        // Create a BillingAmount from a decimal.
        // Return Error if input is not a decimal between 0.0 and 10000.00
        operator fun invoke(v: Double): Either<String, BillingAmount> =
            ConstrainedType.createDecimal("BillingAmount", ::BillingAmount, 0.0, 10000.0, v)

        // Sum a list of prices to make a billing amount
        // Return Error if total is out of bounds
        fun sumPrices(prices: List<Price>): Either<String, BillingAmount> =
            prices.sumOf(Price::value).let(::invoke)
    }
}

// Represents a PDF attachment
data class PdfAttachment(
    val name: String,
    val bytes: ByteArray,
)

@JvmInline
value class PromotionCode(val value: String)
