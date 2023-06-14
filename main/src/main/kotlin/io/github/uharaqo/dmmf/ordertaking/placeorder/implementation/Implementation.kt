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

// priced state is defined Domain.WorkflowTypes

fun interface PriceOrder {
    suspend operator fun invoke(
        getProductPrice: GetProductPrice,
        validatedOrder: ValidatedOrder,
    ): Either<PricingError, PricedOrder>
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
    operator fun invoke(pricedOrder: PricedOrder): HtmlString
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
        pricedOrder: PricedOrder,
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
        val customerInfo = CustomerInfo(
            name = PersonalName(firstName = firstName, lastName = lastName),
            emailAddress = emailAddress,
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
        val address = Address(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            addressLine3 = addressLine3,
            addressLine4 = addressLine4,
            city = city,
            zipCode = zipCode,
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
        val validatedOrder = ValidatedOrder(
            orderId = orderId,
            customerInfo = customerInfo,
            shippingAddress = shippingAddress,
            billingAddress = billingAddress,
            lines = lines,
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
        val pricedLine = PricedOrderLine(
            orderLineId = validatedOrderLine.orderLineId,
            productCode = validatedOrderLine.productCode,
            quantity = validatedOrderLine.quantity,
            linePrice = linePrice,
        )
        pricedLine
    }

val priceOrder = PriceOrder { getProductPrice, validatedOrder ->
    either {
        val lines =
            validatedOrder.lines
                .map(::toPricedOrderLine.partially1(getProductPrice))
                .map { it.bind() }
        val amountToBill =
            lines
                .map(PricedOrderLine::linePrice)
                .let(BillingAmount::sumPrices)
                .mapLeft(::PricingError).bind()
        val pricedOrder = PricedOrder(
            orderId = validatedOrder.orderId,
            customerInfo = validatedOrder.customerInfo,
            shippingAddress = validatedOrder.shippingAddress,
            billingAddress = validatedOrder.billingAddress,
            lines = lines,
            amountToBill = amountToBill,
        )
        pricedOrder
    }
}

// ---------------------------
// AcknowledgeOrder step
// ---------------------------

val acknowledgeOrder = AcknowledgeOrder { createAcknowledgmentLetter, sendAcknowledgment, pricedOrder ->
    val letter = createAcknowledgmentLetter(pricedOrder)
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

fun createOrderPlacedEvent(placedOrder: PricedOrder): OrderPlaced =
    placedOrder

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
    val orderPlacedEvents =
        pricedOrder
            .let(::createOrderPlacedEvent)
            .let(PlaceOrderEvent::OrderPlaced)
            .let(::listOf)
    val billingEvents =
        pricedOrder
            .let(::createBillingEvent)
            ?.let(PlaceOrderEvent::BillableOrderPlaced)
            .let(::listOfOption)

    // return all the events
    listOf(
        acknowledgmentEvents,
        orderPlacedEvents,
        billingEvents,
    ).flatten()
}

// ---------------------------
// overall workflow
// ---------------------------

fun placeOrder(
    checkProductExists: CheckProductCodeExists,
    checkAddressExists: CheckAddressExists,
    getProductPrice: GetProductPrice,
    createOrderAcknowledgmentLetter: CreateOrderAcknowledgmentLetter,
    sendOrderAcknowledgment: SendOrderAcknowledgment,
) = PlaceOrder { unvalidatedOrder: UnvalidatedOrder ->
    either {
        val validatedOrder =
            validateOrder(checkProductExists, checkAddressExists, unvalidatedOrder)
                .mapLeft(PlaceOrderError::Validation).bind()
        val pricedOrder =
            priceOrder(getProductPrice, validatedOrder)
                .mapLeft(PlaceOrderError::Pricing).bind()
        val acknowledgementOption =
            acknowledgeOrder(createOrderAcknowledgmentLetter, sendOrderAcknowledgment, pricedOrder)
        val events =
            createEvents(pricedOrder, acknowledgementOption)
        events
    }
}
