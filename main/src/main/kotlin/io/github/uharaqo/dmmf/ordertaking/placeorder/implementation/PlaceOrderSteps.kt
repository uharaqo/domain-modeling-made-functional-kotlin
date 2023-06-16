package io.github.uharaqo.dmmf.ordertaking.placeorder.implementation

import arrow.core.*
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.*

// ======================================================
// Section 1 : Define each step in the workflow using types
// ======================================================

// ---------------------------
// Validation step
// ---------------------------

// Product validation

fun interface CheckProductCodeExists {
    operator fun invoke(productCode: ProductCode): Boolean
}

// Address validation
enum class AddressValidationError {
    InvalidFormat,
    AddressNotFound,
}

@JvmInline
value class CheckedAddress(val value: UnvalidatedAddress)

fun interface CheckAddressExists {
    suspend operator fun invoke(address: UnvalidatedAddress): Either<AddressValidationError, CheckedAddress>
}

// ---------------------------
// Validated Order
// ---------------------------

sealed interface PricingMethod {
    object Standard : PricingMethod

    @JvmInline
    value class Promotion(val value: PromotionCode) : PricingMethod
}

data class ValidatedOrderLine(
    val orderLineId: OrderLineId,
    val productCode: ProductCode,
    val quantity: OrderQuantity,
)

data class ValidatedOrder(
    val orderId: OrderId,
    val customerInfo: CustomerInfo,
    val shippingAddress: Address,
    val billingAddress: Address,
    val lines: List<ValidatedOrderLine>,
    val pricingMethod: PricingMethod,
)

fun interface ValidateOrder {
    suspend operator fun invoke(
        checkProductCodeExists: CheckProductCodeExists,
        checkAddressExists: CheckAddressExists,
        unvalidatedOrder: UnvalidatedOrder,
    ): Either<ValidationError, ValidatedOrder>
}

// ---------------------------
// Pricing step
// ---------------------------

fun interface GetProductPrice {
    operator fun invoke(productCode: ProductCode): Price
}

fun interface TryGetProductPrice {
    operator fun invoke(productCode: ProductCode): Price?
}

fun interface GetPricingFunction {
    operator fun invoke(pricingMethod: PricingMethod): GetProductPrice
}

fun interface GetStandardPrices {
    operator fun invoke(): GetProductPrice
}

fun interface GetPromotionPrices {
    operator fun invoke(promotionCode: PromotionCode): TryGetProductPrice
}

// priced state
data class PricedOrderProductLine(
    val orderLineId: OrderLineId,
    val productCode: ProductCode,
    val quantity: OrderQuantity,
    val linePrice: Price,
)

sealed interface PricedOrderLine {
    @JvmInline
    value class ProductLine(val value: PricedOrderProductLine) : PricedOrderLine

    @JvmInline
    value class CommentLine(val value: String) : PricedOrderLine
}

data class PricedOrder(
    val orderId: OrderId,
    val customerInfo: CustomerInfo,
    val shippingAddress: Address,
    val billingAddress: Address,
    val amountToBill: BillingAmount,
    val lines: List<PricedOrderLine>,
    val pricingMethod: PricingMethod,
)

fun interface PriceOrder {
    operator fun invoke(
        getPricingFunction: GetPricingFunction,
        validatedOrder: ValidatedOrder,
    ): Either<PricingError, PricedOrder>
}

// ---------------------------
// Shipping
// ---------------------------

enum class ShippingMethod {
    PostalService,
    Fedex24,
    Fedex48,
    Ups48,
}

data class ShippingInfo(
    val shippingMethod: ShippingMethod,
    val shippingCost: Price,
)

data class PricedOrderWithShippingMethod(
    val shippingInfo: ShippingInfo,
    val pricedOrder: PricedOrder,
)

fun interface CalculateShippingCost {
    operator fun invoke(pricedOrder: PricedOrder): Price
}

fun interface AddShippingInfoToOrder {
    operator fun invoke(
        calculateShippingCost: CalculateShippingCost,
        pricedOrder: PricedOrder,
    ): PricedOrderWithShippingMethod
}

// ---------------------------
// VIP shipping
// ---------------------------

fun interface FreeVipShipping {
    operator fun invoke(pricedOrderWithShippingMethod: PricedOrderWithShippingMethod): PricedOrderWithShippingMethod
}

// ---------------------------
// Send OrderAcknowledgment
// ---------------------------
@JvmInline
value class HtmlString(val value: String)

data class OrderAcknowledgment(
    val emailAddress: EmailAddress,
    val letter: HtmlString,
)

fun interface CreateOrderAcknowledgmentLetter {
    operator fun invoke(pricedOrderWithShippingMethod: PricedOrderWithShippingMethod): HtmlString
}

// Send the order acknowledgement to the customer
// Note that this does NOT generate an Result-type error (at least not in this workflow)
// because on failure we will continue anyway.
// On success, we will generate a OrderAcknowledgmentSent event,
// but on failure we won't.

enum class SendResult {
    Sent,
    NotSent,
}

fun interface SendOrderAcknowledgment {
    operator fun invoke(orderAcknowledgment: OrderAcknowledgment): SendResult
}

fun interface AcknowledgeOrder {
    operator fun invoke(
        createAcknowledgmentLetter: CreateOrderAcknowledgmentLetter,
        sendAcknowledgment: SendOrderAcknowledgment,
        pricedOrder: PricedOrderWithShippingMethod,
    ): OrderAcknowledgmentSent?
}

// ---------------------------
// Create events
// ---------------------------

fun interface CreateEvents {
    operator fun invoke(
        pricedOrder: PricedOrder,
        acknowledgmentEventOpt: OrderAcknowledgmentSent?,
    ): List<PlaceOrderEvent>
}
