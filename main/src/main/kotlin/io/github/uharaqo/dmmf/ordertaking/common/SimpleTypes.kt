package io.github.uharaqo.dmmf.ordertaking.common

import arrow.core.*

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
        private const val pattern = ".+@.+"

        // Create an EmailAddress from a string
        // Return Error if input is null, empty, or doesn't have an "@" in it
        operator fun invoke(fieldName: String, str: String): Either<String, EmailAddress> =
            ConstrainedType.createLike(fieldName, ::EmailAddress, pattern, str) // anything separated by an "@"
    }
}

// Customer's VIP status
enum class VipStatus {
    Normal,
    Vip,
    ;

    companion object {
        //  Return a string representation of VipStatus
        fun value(status: VipStatus): String =
            when (status) {
                Normal -> "Normal"
                Vip -> "VIP"
            }

        //  Create a VipStatus from a string
        //  Return Error if input is null, empty, or doesn't match one of the cases
        operator fun invoke(fieldName: String, str: String): Either<String, VipStatus> =
            when (str) {
                "normal", "Normal" -> Normal.right()
                "vip", "VIP" -> Vip.right()
                else -> "$fieldName: Must be one of 'Normal', 'VIP'".left()
            }
    }
}

// A zip code
@JvmInline
value class ZipCode(val value: String) {
    companion object {
        private const val pattern = """\d{5}"""

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
        private const val pattern =
            "^(A[KLRZ]|C[AOT]|D[CE]|FL|GA|HI|I[ADLN]|K[SY]|LA|M[ADEINOST]|N[CDEHJMVY]|O[HKR]|P[AR]|RI|S[CD]|T[NX]|UT|V[AIT]|W[AIVY])$"

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
        private const val pattern = """W\d{4}"""

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
        private const val pattern = """G\d{3}"""

        // Create an GizmoCode from a string
        // Return Error if input is null, empty, or not matching pattern
        operator fun invoke(fieldName: String, code: String): Either<String, GizmoCode> =
            // The codes for Gizmos start with a "G" and then three digits.
            ConstrainedType.createLike(fieldName, ::GizmoCode, pattern, code)
    }
}

// A ProductCode is either a Widget or a Gizmo
sealed interface ProductCode {
    @JvmInline
    value class Widget(val value: WidgetCode) : ProductCode

    @JvmInline
    value class Gizmo(val value: GizmoCode) : ProductCode

    companion object {
        fun value(productCode: ProductCode): String =
            when (productCode) {
                is Gizmo -> productCode.value.value
                is Widget -> productCode.value.value
            }

        // Create an ProductCode from a string
        // Return Error if input is null, empty, or not matching pattern
        operator fun invoke(fieldName: String, code: String?): Either<String, ProductCode> =
            if (code.isNullOrEmpty()) {
                "$fieldName: Must not be null or empty".left()
            } else if (code.startsWith("W")) {
                WidgetCode(fieldName, code)
                    .map(::Widget)
            } else if (code.startsWith("G")) {
                GizmoCode(fieldName, code)
                    .map(::Gizmo)
            } else {
                "$fieldName: Format not recognized '$code'".left()
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

// A Quantity is either a Unit or a Kilogram
sealed interface OrderQuantity {
    @JvmInline
    value class Unit(val value: UnitQuantity) : OrderQuantity

    @JvmInline
    value class Kilogram(val value: KilogramQuantity) : OrderQuantity

    companion object {

        // Return the value inside a OrderQuantity
        fun value(qty: OrderQuantity): Double =
            when (qty) {
                is Unit -> qty.value.value.toDouble()
                is Kilogram -> qty.value.value
            }

        // Create a OrderQuantity from a productCode and quantity
        operator fun invoke(fieldName: String, productCode: ProductCode, quantity: Double): Either<String, OrderQuantity> =
            when (productCode) {
                is ProductCode.Gizmo ->
                    UnitQuantity(fieldName, quantity.toInt())
                        .map(OrderQuantity::Unit)

                is ProductCode.Widget ->
                    KilogramQuantity(fieldName, quantity)
                        .map(OrderQuantity::Kilogram)
            }
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
        fun sumPrices(prices: List<Price>): Either<String, BillingAmount> {
            val total = prices.map(Price::value).sum()
            return invoke(total)
        }
    }
}

// Represents a PDF attachment
data class PdfAttachment(
    val name: String,
    val bytes: ByteArray,
)

@JvmInline
value class PromotionCode(val value: String)

// ===============================
// Reusable constructors and getters for constrained types
// ===============================

// Useful functions for constrained types
object ConstrainedType {

    // Create a constrained string using the constructor provided
    // Return Error if input is null, empty, or length > maxLen
    fun <T> createString(fieldName: String, ctor: (String) -> T, maxLen: Int, str: String?): Either<String, T> =
        if (str.isNullOrEmpty()) {
            "$fieldName must not be null or empty".left()
        } else if (str.length > maxLen) {
            "$fieldName must not be more than $maxLen chars".left()
        } else {
            (ctor(str)).right()
        }

    // Create a optional constrained string using the constructor provided
    // Return None if input is null, empty.
    // Return error if length > maxLen
    // Return Some if the input is valid
    fun <T> createStringOption(fieldName: String, ctor: (String) -> T, maxLen: Int, str: String?): Either<String, T?> =
        if (str.isNullOrEmpty()) {
            null.right()
        } else if (str.length > maxLen) {
            "$fieldName must not be more than $maxLen chars".left()
        } else {
            ctor(str).right()
        }

    // Create a constrained integer using the constructor provided
    // Return Error if input is less than minVal or more than maxVal
    fun <T> createInt(fieldName: String, ctor: (Int) -> T, minVal: Int, maxVal: Int, i: Int): Either<String, T> =
        if (i < minVal) {
            "$fieldName: Must not be less than $minVal".left()
        } else if (i > maxVal) {
            "$fieldName: Must not be greater than $maxVal".left()
        } else {
            ctor(i).right()
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
        if (i < minVal) {
            "$fieldName: Must not be less than $minVal".left()
        } else if (i > maxVal) {
            "$fieldName: Must not be greater than $maxVal".left()
        } else {
            ctor(i).right()
        }

    // Create a constrained string using the constructor provided
    // Return Error if input is null. empty, or does not match the regex pattern
    fun <T> createLike(fieldName: String, ctor: (String) -> T, pattern: String, str: String?): Either<String, T> =
        if (str.isNullOrEmpty()) {
            "$fieldName: Must not be null or empty".left()
        } else if (Regex(pattern).matches(str)) {
            ctor(str).right()
        } else {
            "$fieldName: '$str' must match the pattern '$pattern'".left()
        }
}
