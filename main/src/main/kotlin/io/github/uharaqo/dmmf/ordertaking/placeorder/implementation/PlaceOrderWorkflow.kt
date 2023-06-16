package io.github.uharaqo.dmmf.ordertaking.placeorder.implementation

import arrow.core.raise.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.*

// ---------------------------
// Section 3 : overall workflow
// ---------------------------

interface PlaceOrderDependencies {
    val checkProductExists: CheckProductCodeExists
    val checkAddressExists: CheckAddressExists
    val getPricingFunction: GetPricingFunction
    val calculateShippingCost: CalculateShippingCost
    val createOrderAcknowledgmentLetter: CreateOrderAcknowledgmentLetter
    val sendOrderAcknowledgment: SendOrderAcknowledgment
}

interface PlaceOrderImplementations {
    val validateOrder: ValidateOrder
    val priceOrder: PriceOrder
    val addShippingInfoToOrder: AddShippingInfoToOrder
    val freeVipShipping: FreeVipShipping
    val acknowledgeOrder: AcknowledgeOrder
    val createEvents: CreateEvents
}

context(PlaceOrderDependencies)
fun placeOrder() =
    with(PlaceOrderImpl) {
        placeOrderInternal()
    }

context(PlaceOrderDependencies, PlaceOrderImplementations)
fun placeOrderInternal() = PlaceOrder { unvalidatedOrder: UnvalidatedOrder ->
    either {
        val validatedOrder =
            validateOrder(checkProductExists, checkAddressExists, unvalidatedOrder)
                .mapLeft(PlaceOrderError::Validation).bind()
        val pricedOrder =
            priceOrder(getPricingFunction, validatedOrder)
                .mapLeft(PlaceOrderError::Pricing).bind()
        val pricedOrderWithShipping =
            addShippingInfoToOrder(calculateShippingCost, pricedOrder)
                .let(freeVipShipping::invoke)
        val acknowledgementOption =
            acknowledgeOrder(createOrderAcknowledgmentLetter, sendOrderAcknowledgment, pricedOrderWithShipping)
        val events =
            createEvents(pricedOrder, acknowledgementOption)
        events
    }
}
