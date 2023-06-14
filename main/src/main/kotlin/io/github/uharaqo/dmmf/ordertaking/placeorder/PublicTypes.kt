package io.github.uharaqo.dmmf.ordertaking.placeorder

import arrow.core.Either
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.*
import java.net.URI

// We are defining types and submodules, so we can use a namespace
// rather than a module at the top level

// ==================================
// This file contains the definitions of PUBLIC types (exposed at the boundary of the bounded context)
// related to the PlaceOrder workflow
// ==================================

// ------------------------------------
// inputs to the workflow

data class UnvalidatedCustomerInfo(
    val firstName: String,
    val lastName: String,
    val emailAddress: String,
    val vipStatus: String,
)

data class UnvalidatedAddress(
    val addressLine1: String,
    val addressLine2: String,
    val addressLine3: String,
    val addressLine4: String,
    val city: String,
    val zipCode: String,
    val state: String,
    val country: String,
)

data class UnvalidatedOrderLine(
    val orderLineId: String,
    val productCode: String,
    val quantity: Double,
)

data class UnvalidatedOrder(
    val orderId: String,
    val customerInfo: UnvalidatedCustomerInfo,
    val shippingAddress: UnvalidatedAddress,
    val billingAddress: UnvalidatedAddress,
    val lines: List<UnvalidatedOrderLine>,
    val promotionCode: String,
)

// ------------------------------------
// outputs from the workflow (success case)

// Event will be created if the Acknowledgment was successfully posted
data class OrderAcknowledgmentSent(
    val orderId: OrderId,
    val emailAddress: EmailAddress,
)

// priced state
data class ShippableOrderLine(
    val productCode: ProductCode,
    val quantity: OrderQuantity,
)

data class ShippableOrderPlaced(
    val orderId: OrderId,
    val shippingAddress: Address,
    val shipmentLines: List<ShippableOrderLine>,
    val pdf: PdfAttachment,
)

// Event to send to shipping context
typealias OrderPlaced = PricedOrder

// Event to send to billing context
// Will only be created if the AmountToBill is not zero
data class BillableOrderPlaced(
    val orderId: OrderId,
    val billingAddress: Address,
    val amountToBill: BillingAmount,
)

// The possible events resulting from the PlaceOrder workflow
// Not all events will occur, depending on the logic of the workflow
sealed interface PlaceOrderEvent {
    @JvmInline
    value class ShippableOrderPlaced(val value: io.github.uharaqo.dmmf.ordertaking.placeorder.ShippableOrderPlaced) : PlaceOrderEvent

    @JvmInline
    value class BillableOrderPlaced(val value: io.github.uharaqo.dmmf.ordertaking.placeorder.BillableOrderPlaced) :
        PlaceOrderEvent

    @JvmInline
    value class AcknowledgmentSent(val value: io.github.uharaqo.dmmf.ordertaking.placeorder.OrderAcknowledgmentSent) : PlaceOrderEvent
}

// ------------------------------------
// error outputs

// All the things that can go wrong in this workflow
@JvmInline
value class ValidationError(val value: String)

@JvmInline
value class PricingError(val value: String)

data class ServiceInfo(
    val name: String,
    val endpoint: URI,
)

data class RemoteServiceError(
    val service: ServiceInfo,
    val exception: Exception,
)

sealed interface PlaceOrderError {
    @JvmInline
    value class Validation(val value: ValidationError) : PlaceOrderError

    @JvmInline
    value class Pricing(val value: PricingError) : PlaceOrderError

    @JvmInline
    value class RemoteService(val value: RemoteServiceError) : PlaceOrderError
}

// ------------------------------------
// the workflow itself

fun interface PlaceOrder {
    suspend operator fun invoke(unvalidatedOrder: UnvalidatedOrder): Either<PlaceOrderError, List<PlaceOrderEvent>>
}
