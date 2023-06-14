package io.github.uharaqo.dmmf.ordertaking.placeorder.implementation

import arrow.core.*
import arrow.core.raise.*
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.*
import kotlin.collections.flatten
import kotlin.contracts.ExperimentalContracts

// ======================================================
// This file contains the final implementation for the PlaceOrder workflow
//
// This represents the code in chapter 10, "Working with Errors"
//
// There are two parts:
// * the first section contains the (type-only) definitions for each step
// * the second section contains the implementations for each step
//   and the implementation of the overall workflow
// ======================================================

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

// ======================================================
// Section 2 : Implementation
// ======================================================

// ---------------------------
// ValidateOrder step
// ---------------------------

fun toCustomerInfo(unvalidatedCustomerInfo: UnvalidatedCustomerInfo): Either<ValidationError, CustomerInfo> =
    either {
        val firstName =
            unvalidatedCustomerInfo.firstName
                .let(String50.Companion::create.partially1("FirstName")).bind()
        val lastName =
            unvalidatedCustomerInfo.lastName
                .let(String50.Companion::create.partially1("LastName")).bind()
        val emailAddress =
            unvalidatedCustomerInfo.emailAddress
                .let(EmailAddress.Companion::create.partially1("EmailAddress")).bind()
        val vipStatus =
            unvalidatedCustomerInfo.vipStatus
                .let(VipStatus.Companion::create.partially1("vipStatus")).bind()
        val customerInfo = CustomerInfo(
            name = PersonalName(firstName = firstName, lastName = lastName),
            emailAddress = emailAddress,
            vipStatus = vipStatus,
        )
        customerInfo
    }.mapLeft(::ValidationError)

fun toAddress(checkedAddress: CheckedAddress): Either<ValidationError, Address> =
    either {
        val addr = checkedAddress.value
        val addressLine1 =
            addr.addressLine1
                .let(String50.Companion::create.partially1("AddressLine1")).bind()
        val addressLine2 =
            addr.addressLine2
                .let(String50.Companion::createOption.partially1("AddressLine2")).bind()
        val addressLine3 =
            addr.addressLine3
                .let(String50.Companion::createOption.partially1("AddressLine3")).bind()
        val addressLine4 =
            addr.addressLine4
                .let(String50.Companion::createOption.partially1("AddressLine4")).bind()
        val city =
            addr.city
                .let(String50.Companion::create.partially1("City")).bind()
        val zipCode =
            addr.zipCode
                .let(ZipCode.Companion::create.partially1("ZipCode")).bind()
        val state =
            addr.state
                .let(UsStateCode.Companion::create.partially1("State")).bind()
        val country =
            addr.country
                .let(String50.Companion::create.partially1("Country")).bind()
        val address = Address(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            addressLine3 = addressLine3,
            addressLine4 = addressLine4,
            city = city,
            zipCode = zipCode,
            state = state,
            country = country,
        )
        address
    }.mapLeft(::ValidationError)

// Call the checkAddressExists and convert the error to a ValidationError
suspend fun toCheckedAddress(
    checkAddress: CheckAddressExists,
    address: UnvalidatedAddress,
): Either<ValidationError, CheckedAddress> =
    address
        .let { checkAddress(it) }
        .mapLeft { addrError ->
            when (addrError) {
                AddressValidationError.InvalidFormat -> ValidationError("Address not found")
                AddressValidationError.AddressNotFound -> ValidationError("Address has bad format")
            }
        }

fun toOrderId(orderId: String): Either<ValidationError, OrderId> =
    orderId
        .let(OrderId.Companion::create.partially1("OrderId"))
        .mapLeft(::ValidationError)

// Helper function for validateOrder
fun toOrderLineId(orderId: String): Either<ValidationError, OrderLineId> =
    orderId
        .let(OrderLineId.Companion::create.partially1("OrderLineId"))
        .mapLeft(::ValidationError)

// Helper function for validateOrder
fun toProductCode(
    checkProductCodeExists: CheckProductCodeExists,
    productCode: String,
): Either<ValidationError, ProductCode> {
    // create a ProductCode -> Result<ProductCode,...> function
    // suitable for using in a pipeline
    val checkProduct = { _productCode: ProductCode ->
        if (checkProductCodeExists(_productCode)) {
            _productCode.right()
        } else {
            ValidationError("Invalid: $_productCode").left()
        }
    }

    // assemble the pipeline
    return productCode
        .let(ProductCode.Companion::create.partially1("ProductCode"))
        .mapLeft(::ValidationError)
        .flatMap { checkProduct(it) }
}

// Helper function for validateOrder
fun toOrderQuantity(productCode: ProductCode, quantity: Double): Either<ValidationError, OrderQuantity> =
    OrderQuantity.create("OrderQuantity", productCode, quantity)
        .mapLeft(::ValidationError)

// Helper function for validateOrder
fun toValidatedOrderLine(
    checkProductExists: CheckProductCodeExists,
    unvalidatedOrderLine: UnvalidatedOrderLine,
): Either<ValidationError, ValidatedOrderLine> =
    either {
        val orderLineId =
            unvalidatedOrderLine.orderLineId
                .let(::toOrderLineId).bind()
        val productCode =
            unvalidatedOrderLine.productCode
                .let(::toProductCode.partially1(checkProductExists)).bind()
        val quantity =
            unvalidatedOrderLine.quantity
                .let(::toOrderQuantity.partially1(productCode)).bind()
        val validatedOrderLine = ValidatedOrderLine(
            orderLineId = orderLineId,
            productCode = productCode,
            quantity = quantity,
        )
        validatedOrderLine
    }

@OptIn(ExperimentalContracts::class)
suspend inline fun <T, R> T.letSuspend(crossinline block: suspend (T) -> R): R {
    kotlin.contracts.contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return block(this)
}

val validateOrder = ValidateOrder { checkProductCodeExists, checkAddressExists, unvalidatedOrder ->
    either {
        val orderId =
            unvalidatedOrder.orderId
                .let(::toOrderId).bind()
        val customerInfo =
            unvalidatedOrder.customerInfo
                .let(::toCustomerInfo).bind()
        val checkedShippingAddress =
            unvalidatedOrder.shippingAddress
                .letSuspend(::toCheckedAddress.partially1(checkAddressExists)).bind()
        val shippingAddress =
            checkedShippingAddress
                .let(::toAddress).bind()
        val checkedBillingAddress =
            unvalidatedOrder.billingAddress
                .letSuspend(::toCheckedAddress.partially1(checkAddressExists)).bind()
        val billingAddress =
            checkedBillingAddress
                .let(::toAddress).bind()
        val lines =
            unvalidatedOrder.lines
                .map(::toValidatedOrderLine.partially1(checkProductCodeExists))
                .map { it.bind() }
        val pricingMethod =
            unvalidatedOrder.promotionCode
                .let(Pricing::createPricingMethod)
        val validatedOrder = ValidatedOrder(
            orderId = orderId,
            customerInfo = customerInfo,
            shippingAddress = shippingAddress,
            billingAddress = billingAddress,
            lines = lines,
            pricingMethod = pricingMethod,
        )
        validatedOrder
    }
}

// ---------------------------
// PriceOrder step
// ---------------------------

fun toPricedOrderLine(
    getProductPrice: GetProductPrice,
    validatedOrderLine: ValidatedOrderLine,
): Either<PricingError, PricedOrderLine> =
    either {
        val qty = validatedOrderLine.quantity.let(OrderQuantity::value)
        val price = validatedOrderLine.productCode.let(getProductPrice::invoke)
        val linePrice = Price.multiply(qty, price).mapLeft(::PricingError).bind()
        val pricedLine = PricedOrderProductLine(
            orderLineId = validatedOrderLine.orderLineId,
            productCode = validatedOrderLine.productCode,
            quantity = validatedOrderLine.quantity,
            linePrice = linePrice,
        )
        pricedLine.let(PricedOrderLine::ProductLine)
    }

// add the special comment line if needed
fun addCommentLine(pricingMethod: PricingMethod, lines: List<PricedOrderLine>) =
    when (pricingMethod) {
        PricingMethod.Standard ->
            // unchanged
            lines

        is PricingMethod.Promotion ->
            "Applied promotion ${pricingMethod.value.value}"
                .let(PricedOrderLine::CommentLine)
                .let { lines.plus(it) }
    }

fun getLinePrice(line: PricedOrderLine) =
    when (line) {
        is PricedOrderLine.ProductLine -> line.value.linePrice
        is PricedOrderLine.CommentLine -> Price.unsafeCreate(0.0)
    }

val priceOrder = PriceOrder { getPricingFunction, validatedOrder ->
    either {
        val getProductPrice = getPricingFunction::invoke.partially1(validatedOrder.pricingMethod)()
        val lines =
            validatedOrder.lines
                .map(::toPricedOrderLine.partially1(getProductPrice))
                .map { it.bind() }
                .let(::addCommentLine.partially1(validatedOrder.pricingMethod))
        val amountToBill =
            lines
                .map(::getLinePrice)
                .let(BillingAmount::sumPrices)
                .mapLeft(::PricingError).bind()
        val pricedOrder = PricedOrder(
            orderId = validatedOrder.orderId,
            customerInfo = validatedOrder.customerInfo,
            shippingAddress = validatedOrder.shippingAddress,
            billingAddress = validatedOrder.billingAddress,
            lines = lines,
            amountToBill = amountToBill,
            pricingMethod = validatedOrder.pricingMethod,
        )
        pricedOrder
    }
}

// ---------------------------
// Shipping step
// ---------------------------

private enum class ShippingAddressType {
    UsLocalState,
    UsRemoteState,
    International,
    ;

    companion object {
        fun from(address: Address): ShippingAddressType =
            if (address.country.value == "US") {
                when (address.state.value) {
                    "CA", "OR", "AZ", "NV" -> UsLocalState
                    else -> UsRemoteState
                }
            } else {
                International
            }
    }
}

val calculateShippingCost = CalculateShippingCost { pricedOrder ->
    when (ShippingAddressType.from(pricedOrder.shippingAddress)) {
        ShippingAddressType.UsLocalState -> 5.0
        ShippingAddressType.UsRemoteState -> 10.0
        ShippingAddressType.International -> 20.0
    }
        .let(Price::unsafeCreate)
}

val addShippingInfoToOrder = AddShippingInfoToOrder { calculateShippingCost, pricedOrder ->
    // create the shipping info
    val shippingInfo = ShippingInfo(
        shippingMethod = ShippingMethod.Fedex24,
        shippingCost = calculateShippingCost(pricedOrder),
    )
// add it to the order
    PricedOrderWithShippingMethod(shippingInfo, pricedOrder)
}

// ---------------------------
// VIP shipping step
// ---------------------------

//  Update the shipping cost if customer is VIP
val freeVipShipping = FreeVipShipping { order ->
    val updatedShippingInfo =
        when (order.pricedOrder.customerInfo.vipStatus) {
            VipStatus.Normal ->
                // untouched
                order.shippingInfo

            VipStatus.Vip ->
                ShippingInfo(
                    shippingCost = Price.unsafeCreate(0.0),
                    shippingMethod = ShippingMethod.Fedex24,
                )
        }

    order.copy(shippingInfo = updatedShippingInfo)
}

// ---------------------------
// AcknowledgeOrder step
// ---------------------------

val acknowledgeOrder = AcknowledgeOrder { createAcknowledgmentLetter, sendAcknowledgment, pricedOrderWithShipping ->
    val pricedOrder = pricedOrderWithShipping.pricedOrder
    val letter = createAcknowledgmentLetter(pricedOrderWithShipping)
    val acknowledgment = OrderAcknowledgment(
        emailAddress = pricedOrder.customerInfo.emailAddress,
        letter = letter,
    )

    // if the acknowledgement was successfully sent,
    // return the corresponding event, else return None
    when (sendAcknowledgment(acknowledgment)) {
        SendResult.Sent ->
            OrderAcknowledgmentSent(
                orderId = pricedOrder.orderId,
                emailAddress = pricedOrder.customerInfo.emailAddress,
            )

        SendResult.NotSent ->
            null
    }
}

// ---------------------------
// Create events
// ---------------------------

fun makeShipmentLine(line: PricedOrderLine): ShippableOrderLine? =
    when (line) {
        is PricedOrderLine.ProductLine ->
            ShippableOrderLine(
                productCode = line.value.productCode,
                quantity = line.value.quantity,
            )

        is PricedOrderLine.CommentLine -> null
    }

fun createShippingEvent(placedOrder: PricedOrder): ShippableOrderPlaced =
    ShippableOrderPlaced(
        orderId = placedOrder.orderId,
        shippingAddress = placedOrder.shippingAddress,
        shipmentLines = placedOrder.lines.mapNotNull(::makeShipmentLine),
        pdf = PdfAttachment(
            name = "Order${placedOrder.orderId.let(OrderId::value)}.pdf",
            bytes = ByteArray(0),
        ),
    )

fun createBillingEvent(placedOrder: PricedOrder): BillableOrderPlaced? {
    val billingAmount = placedOrder.amountToBill.let(BillingAmount::value)
    return if (billingAmount > 0.0) {
        BillableOrderPlaced(
            orderId = placedOrder.orderId,
            billingAddress = placedOrder.billingAddress,
            amountToBill = placedOrder.amountToBill,
        )
    } else {
        null
    }
}

// helper to convert an Option into a List
fun <T> listOfOption(opt: T?): List<T> =
    opt?.let { listOf(it) } ?: emptyList()

val createEvents = CreateEvents { pricedOrder, acknowledgmentEventOpt ->
    val acknowledgmentEvents =
        acknowledgmentEventOpt
            ?.let(PlaceOrderEvent::AcknowledgmentSent)
            .let(::listOfOption)
    val shippingEvents =
        pricedOrder
            .let(::createShippingEvent)
            .let(PlaceOrderEvent::ShippableOrderPlaced)
            .let(::listOf)
    val billingEvents =
        pricedOrder
            .let(::createBillingEvent)
            ?.let(PlaceOrderEvent::BillableOrderPlaced)
            .let(::listOfOption)

    // return all the events
    listOf(
        acknowledgmentEvents,
        shippingEvents,
        billingEvents,
    ).flatten()
}

// ---------------------------
// overall workflow
// ---------------------------

fun placeOrder(
    checkProductExists: CheckProductCodeExists,
    checkAddressExists: CheckAddressExists,
    getPricingFunction: GetPricingFunction,
    calculateShippingCost: CalculateShippingCost,
    createOrderAcknowledgmentLetter: CreateOrderAcknowledgmentLetter,
    sendOrderAcknowledgment: SendOrderAcknowledgment,
) = PlaceOrder { unvalidatedOrder: UnvalidatedOrder ->
    either {
        val validatedOrder =
            validateOrder(checkProductExists, checkAddressExists, unvalidatedOrder)
                .mapLeft(PlaceOrderError::Validation).bind()
        val pricedOrder =
            priceOrder(getPricingFunction, validatedOrder)
                .mapLeft(PlaceOrderError::Pricing).bind()
        val pricedOrderWithShipping =
            pricedOrder
                .let(addShippingInfoToOrder::invoke.partially1(calculateShippingCost))
                .let(freeVipShipping::invoke)
        val acknowledgementOption =
            acknowledgeOrder(createOrderAcknowledgmentLetter, sendOrderAcknowledgment, pricedOrderWithShipping)
        val events =
            createEvents(pricedOrder, acknowledgementOption)
        events
    }
}
