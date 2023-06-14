package io.github.uharaqo.dmmf.ordertaking.placeorder

import arrow.core.*
import arrow.core.raise.*
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.*

// ======================================================
// This file contains the logic for working with data transfer objects (DTOs)
//
// This represents the code in chapter 11, "Serialization"
//
// Each type of DTO is defined using primitive, serializable types
// and then there are `toDomain` and `fromDomain` functions defined for each DTO.
//
// ======================================================

// ==================================
// DTOs for PlaceOrder workflow
// ==================================

// ===============================================
// DTO for CustomerInfo
// ===============================================

data class CustomerInfoDto(
    val firstName: String,
    val lastName: String,
    val emailAddress: String,
    val vipStatus: String,
) {
    companion object {
        // Convert the DTO into a UnvalidatedCustomerInfo object.
        // This always succeeds because there is no validation.
        // Used when importing an OrderForm from the outside world into the domain.
        fun toUnvalidatedCustomerInfo(dto: CustomerInfoDto): UnvalidatedCustomerInfo =
            // sometimes it's helpful to use an explicit type annotation
            // to avoid ambiguity between records with the same field names.
            UnvalidatedCustomerInfo(
                // this is a simple 1:1 copy which always succeeds
                firstName = dto.firstName,
                lastName = dto.lastName,
                emailAddress = dto.emailAddress,
                vipStatus = dto.vipStatus,
            )

        // Convert the DTO into a CustomerInfo object
        // Used when importing from the outside world into the domain, eg loading from a database
        fun toCustomerInfo(dto: CustomerInfoDto): Either<String, CustomerInfo> =
            either {
                // get each (validated) simple type from the DTO as a success or failure
                val first = String50("FirstName", dto.firstName).bind()
                val last = String50("LastName", dto.lastName).bind()
                val email = EmailAddress("EmailAddress", dto.emailAddress).bind()
                val vipStatus = VipStatus("VipStatus", dto.vipStatus).bind()
                // combine the components to create the domain object
                val name = PersonalName(firstName = first, lastName = last)
                val info = CustomerInfo(name = name, emailAddress = email, vipStatus = vipStatus)
                info
            }

        // Convert a CustomerInfo object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromCustomerInfo(domainObj: CustomerInfo): CustomerInfoDto =
            // this is a simple 1:1 copy
            CustomerInfoDto(
                firstName = domainObj.name.firstName.value,
                lastName = domainObj.name.lastName.value,
                emailAddress = domainObj.emailAddress.value,
                vipStatus = domainObj.vipStatus.let(VipStatus::value),
            )
    }
}

// ===============================================
//  DTO for Address
// ===============================================

data class AddressDto(
    val addressLine1: String,
    val addressLine2: String,
    val addressLine3: String,
    val addressLine4: String,
    val city: String,
    val zipCode: String,
    val state: String,
    val country: String,
) {
    companion object {
        // Convert the DTO into a UnvalidatedAddress
        // This always succeeds because there is no validation.
        // Used when importing an OrderForm from the outside world into the domain.
        fun toUnvalidatedAddress(dto: AddressDto): UnvalidatedAddress =
            // this is a simple 1:1 copy
            UnvalidatedAddress(
                addressLine1 = dto.addressLine1,
                addressLine2 = dto.addressLine2,
                addressLine3 = dto.addressLine3,
                addressLine4 = dto.addressLine4,
                city = dto.city,
                zipCode = dto.zipCode,
                state = dto.state,
                country = dto.country,
            )

        // Convert the DTO into a Address object
        // Used when importing from the outside world into the domain, eg loading from a database.
        fun toAddress(dto: AddressDto): Either<String, Address> =
            either {
                // get each (validated) simple type from the DTO as a success or failure
                val addressLine1 = String50("AddressLine1", dto.addressLine1).bind()
                val addressLine2 = String50.optional("AddressLine2", dto.addressLine2).bind()
                val addressLine3 = String50.optional("AddressLine3", dto.addressLine3).bind()
                val addressLine4 = String50.optional("AddressLine4", dto.addressLine4).bind()
                val city = String50("City", dto.city).bind()
                val zipCode = ZipCode("ZipCode", dto.zipCode).bind()
                val state = UsStateCode("State", dto.state).bind()
                val country = String50("Country", dto.country).bind()

                // combine the components to create the domain object
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
            }

        // Convert a Address object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromAddress(domainObj: Address): AddressDto =
            // this is a simple 1:1 copy
            AddressDto(
                addressLine1 = domainObj.addressLine1.value,
                addressLine2 = domainObj.addressLine2!!.value,
                addressLine3 = domainObj.addressLine3!!.value,
                addressLine4 = domainObj.addressLine4!!.value,
                city = domainObj.city.value,
                zipCode = domainObj.zipCode.value,
                state = domainObj.state.value,
                country = domainObj.country.value,
            )
    }

// ===============================================
// DTOs for OrderLines
// ===============================================

    // From the order form used as input
    data class OrderFormLineDto(
        val orderLineId: String,
        val productCode: String,
        val quantity: Double,
    ) {
        companion object {
            // Convert the OrderFormLine into a UnvalidatedOrderLine
            // This always succeeds because there is no validation.
            // Used when importing an OrderForm from the outside world into the domain.
            fun toUnvalidatedOrderLine(dto: OrderFormLineDto): UnvalidatedOrderLine =
                // this is a simple 1:1 copy
                UnvalidatedOrderLine(
                    orderLineId = dto.orderLineId,
                    productCode = dto.productCode,
                    quantity = dto.quantity,
                )
        }
    }
}

// ===============================================
// DTOs for PricedOrderLines
// ===============================================

// Used in the output of the workflow
data class PricedOrderLineDto(
    val orderLineId: String?,
    val productCode: String?,
    val quantity: Double,
    val linePrice: Double,
    val comment: String,
) {
    companion object {
        // Convert a PricedOrderLine object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromDomain(domainObj: PricedOrderLine): PricedOrderLineDto =
            when (domainObj) {
                is PricedOrderLine.ProductLine ->
                    // this is a simple 1:1 copy
                    PricedOrderLineDto(
                        orderLineId = domainObj.value.orderLineId.value,
                        productCode = domainObj.value.productCode.let(ProductCode::value),
                        quantity = domainObj.value.quantity.let(OrderQuantity::value),
                        linePrice = domainObj.value.linePrice.value,
                        comment = "",
                    )

                is PricedOrderLine.CommentLine ->
                    PricedOrderLineDto(
                        orderLineId = null,
                        productCode = null,
                        quantity = 0.0,
                        linePrice = 0.0,
                        comment = domainObj.value,
                    )
            }
    }
}

// ===============================================
//  DTO for OrderForm
// ===============================================
data class OrderFormDto(
    val orderId: String,
    val customerInfo: CustomerInfoDto,
    val shippingAddress: AddressDto,
    val billingAddress: AddressDto,
    val lines: List<AddressDto.OrderFormLineDto>,
    val promotionCode: String,
) {
    companion object {
        // Convert the OrderForm into a UnvalidatedOrder
        // This always succeeds because there is no validation.
        fun toUnvalidatedOrder(dto: OrderFormDto): UnvalidatedOrder =
            UnvalidatedOrder(
                orderId = dto.orderId,
                customerInfo = dto.customerInfo.let(CustomerInfoDto::toUnvalidatedCustomerInfo),
                shippingAddress = dto.shippingAddress.let(AddressDto::toUnvalidatedAddress),
                billingAddress = dto.billingAddress.let(AddressDto::toUnvalidatedAddress),
                lines = dto.lines.map(AddressDto.OrderFormLineDto::toUnvalidatedOrderLine),
                promotionCode = dto.promotionCode,
            )
    }
}

// ===============================================
//  DTO for ShippableOrderPlaced event
// ===============================================

data class ShippableOrderLineDto(
    val productCode: String,
    val quantity: Double,
)

// Event to send to shipping context
data class ShippableOrderPlacedDto(
    val orderId: String,
    val shippingAddress: AddressDto,
    val shipmentLines: List<ShippableOrderLineDto>,
    val pdf: PdfAttachment,
) {
    companion object {
        fun fromShippableOrderLine(domainObj: ShippableOrderLine): ShippableOrderLineDto =
            ShippableOrderLineDto(
                productCode = domainObj.productCode.let(ProductCode::value),
                quantity = domainObj.quantity.let(OrderQuantity::value),
            )

        // Convert a ShippableOrderPlaced object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromDomain(domainObj: ShippableOrderPlaced): ShippableOrderPlacedDto =
            ShippableOrderPlacedDto(
                orderId = domainObj.orderId.value,
                shippingAddress = domainObj.shippingAddress.let(AddressDto::fromAddress),
                shipmentLines = domainObj.shipmentLines.map(::fromShippableOrderLine),
                pdf = domainObj.pdf,
            )
    }
}

// ===============================================
//  DTO for BillableOrderPlaced event
// ===============================================

// Event to send to billing context
data class BillableOrderPlacedDto(
    val orderId: String,
    val billingAddress: AddressDto,
    val amountToBill: Double,
) {
    companion object {
        // Convert a BillableOrderPlaced object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromDomain(domainObj: BillableOrderPlaced): BillableOrderPlacedDto =
            BillableOrderPlacedDto(
                orderId = domainObj.orderId.value,
                billingAddress = domainObj.billingAddress.let(AddressDto::fromAddress),
                amountToBill = domainObj.amountToBill.let(BillingAmount::value),
            )
    }
}

// ===============================================
//  DTO for OrderAcknowledgmentSent event
// ===============================================

// Event to send to other bounded contexts
data class OrderAcknowledgmentSentDto(
    val orderId: String,
    val emailAddress: String,
) {
    companion object {
        // Convert a OrderAcknowledgmentSent object into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromDomain(domainObj: OrderAcknowledgmentSent): OrderAcknowledgmentSentDto =
            OrderAcknowledgmentSentDto(
                orderId = domainObj.orderId.value,
                emailAddress = domainObj.emailAddress.value,
            )
    }
}

// ===============================================
// DTO for PlaceOrderEvent
// ===============================================

// Use a dictionary representation of a PlaceOrderEvent, suitable for JSON
// See "Serializing Records and Choice Types Using Maps" in chapter 11
@JvmInline
value class PlaceOrderEventDto(val value: Map<String, Any>) {
    companion object {
        // Convert a PlaceOrderEvent into the corresponding DTO.
        // Used when exporting from the domain to the outside world.
        fun fromDomain(domainObj: PlaceOrderEvent): PlaceOrderEventDto =
            when (domainObj) {
                is PlaceOrderEvent.ShippableOrderPlaced ->
                    domainObj.value.let(ShippableOrderPlacedDto::fromDomain)
                        .let { "ShippableOrderPlaced" to (it as Any) }

                is PlaceOrderEvent.BillableOrderPlaced ->
                    domainObj.value.let(BillableOrderPlacedDto::fromDomain)
                        .let { "BillableOrderPlaced" to (it as Any) }

                is PlaceOrderEvent.AcknowledgmentSent ->
                    domainObj.value.let(OrderAcknowledgmentSentDto::fromDomain)
                        .let { "OrderAcknowledgmentSent" to (it as Any) }
            }
                .let(::mapOf)
                .let(::PlaceOrderEventDto)
    }
}

// ===============================================
//  DTO for PlaceOrderError
// ===============================================

data class PlaceOrderErrorDto(
    val code: String,
    val message: String,
) {
    companion object {
        fun fromDomain(domainObj: PlaceOrderError): PlaceOrderErrorDto =
            when (domainObj) {
                is PlaceOrderError.Validation ->
                    PlaceOrderErrorDto(
                        code = "ValidationError",
                        message = domainObj.value.value,
                    )

                is PlaceOrderError.Pricing ->
                    PlaceOrderErrorDto(
                        code = "PricingError",
                        message = domainObj.value.value,
                    )

                is PlaceOrderError.RemoteService ->
                    PlaceOrderErrorDto(
                        code = "RemoteServiceError",
                        message = "${domainObj.value.service.name}: ${domainObj.value.exception.message}",
                    )
            }
    }
}
