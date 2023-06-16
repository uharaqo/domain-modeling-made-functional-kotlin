package io.github.uharaqo.dmmf.ordertaking.common

import arrow.core.Either

// A Quantity is either a Unit or a Kilogram
sealed interface OrderQuantity {
    val quantity: Double

    @JvmInline
    value class Unit(val value: UnitQuantity) : OrderQuantity {
        override val quantity: Double
            get() = value.value.toDouble()
    }

    @JvmInline
    value class Kilogram(val value: KilogramQuantity) : OrderQuantity {
        override val quantity: Double
            get() = value.value
    }

    companion object {
        // Create a OrderQuantity from a productCode and quantity
        operator fun invoke(
            fieldName: String,
            productCode: ProductCode,
            quantity: Double,
        ): Either<String, OrderQuantity> =
            when (productCode) {
                is ProductCode.Gizmo -> UnitQuantity(fieldName, quantity.toInt()).map(OrderQuantity::Unit)
                is ProductCode.Widget -> KilogramQuantity(fieldName, quantity).map(OrderQuantity::Kilogram)
            }
    }
}
