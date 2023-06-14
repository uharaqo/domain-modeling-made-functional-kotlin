package io.github.uharaqo.dmmf.ordertaking.placeorder.api

import arrow.core.Either
import arrow.core.right
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.*

// ======================================================
// This file contains the complete workflow, exposed as a JSON API
//
// 1) The HttpRequest is turned into a DTO, which is then turned into a Domain object
// 2) The main workflow function is called
// 3) The output is turned into a DTO which is turned into a HttpResponse
// ======================================================

typealias JsonString = String

// Very simplified version!
data class HttpRequest(
    val action: String,
    val uri: String,
    val body: JsonString,
)

// Very simplified version!
data class HttpResponse(
    val httpStatusCode: Int,
    val body: JsonString,
)

// An API takes a HttpRequest as input and returns a async response
fun interface PlaceOrderApi {
    suspend operator fun invoke(request: HttpRequest): HttpResponse
}

// =============================
// JSON serialization
// =============================

fun serializeJson(): (Any) -> String = TODO()
fun <T> deserializeJson(str: String): T = TODO()

// =============================
// Implementation
// =============================

// setup dummy dependencies

private val checkProductExists = CheckProductCodeExists { productCode -> true }

private val checkAddressExists = CheckAddressExists { unvalidatedAddress ->
    CheckedAddress(unvalidatedAddress).right()
}

private val getStandardPrices = GetStandardPrices {
    GetProductPrice { productCode ->
        Price.unsafeCreate(10.0)
    }
}

private val getPromotionPrices: (PromotionCode) -> TryGetProductPrice = { promotionCode: PromotionCode ->
    val halfPricePromotion =
        TryGetProductPrice { productCode ->
            if (ProductCode.value(productCode) == "ONSALE") {
                Price.unsafeCreate(5.0)
            } else {
                null
            }
        }

    val quarterPricePromotion = TryGetProductPrice { productCode ->
        if (ProductCode.value(productCode) == "ONSALE") {
            Price.unsafeCreate(2.5)
        } else {
            null
        }
    }

    val noPromotion = TryGetProductPrice { productCode -> null }

    when (promotionCode.value) {
        "HALF" -> halfPricePromotion
        "QUARTER" -> quarterPricePromotion
        else -> noPromotion
    }
}

private val getPricingFunction: GetPricingFunction =
    Pricing.getPricingFunction(getStandardPrices, getPromotionPrices)

private val calculateShippingCost =
    io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.calculateShippingCost

private val createOrderAcknowledgmentLetter = CreateOrderAcknowledgmentLetter { pricedOrder ->
    HtmlString("some text")
}

private val sendOrderAcknowledgment = SendOrderAcknowledgment { orderAcknowledgement ->
    SendResult.Sent
}

// -------------------------------
// workflow
// -------------------------------

// This function converts the workflow output into a HttpResponse
val workflowResultToHttpReponse = { result: Either<PlaceOrderError, List<PlaceOrderEvent>> ->
    result.fold(
        { err ->
            // turn domain errors into a dto
            val dto = err.let(PlaceOrderErrorDto::fromDomain)
            // and serialize to JSON
            val json = serializeJson()(dto)
            val response =
                HttpResponse(
                    httpStatusCode = 401,
                    body = json,
                )
            response
        },
        { events ->
            // turn domain events into dtos
            val dtos =
                events
                    .map(PlaceOrderEventDto::fromDomain)
            // and serialize to JSON
            val json = serializeJson()(dtos)
            val response =
                HttpResponse(
                    httpStatusCode = 200,
                    body = json,
                )
            response
        },
    )
}

val placeOrderApi = PlaceOrderApi { request ->
    // following the approach in "A Complete Serialization Pipeline" in chapter 11

    // start with a string
    val orderFormJson = request.body
    val orderForm = deserializeJson<OrderFormDto>(orderFormJson)
    // convert to domain object
    val unvalidatedOrder = orderForm.let(OrderFormDto::toUnvalidatedOrder)

    // setup the dependencies. See "Injecting Dependencies" in chapter 9
    val workflow =
        placeOrder(
            checkProductExists, // dependency
            checkAddressExists, // dependency
            getPricingFunction, // dependency
            calculateShippingCost, // dependency
            createOrderAcknowledgmentLetter, // dependency
            sendOrderAcknowledgment, // dependency
        )

    // now we are in the pure domain
    val asyncResult = workflow(unvalidatedOrder)

    // now convert from the pure domain back to a HttpResponse
    asyncResult.let(workflowResultToHttpReponse)
}
