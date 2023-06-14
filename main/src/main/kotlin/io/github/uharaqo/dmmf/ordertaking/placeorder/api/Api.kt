package io.github.uharaqo.dmmf.ordertaking.placeorder.api

import arrow.core.*
import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

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

fun serializeJson(v: Any): String = v.toString() // TODO
fun <T> deserializeJson(serializer: KSerializer<T>, json: String): T = Json.decodeFromString(serializer, json)

// =============================
// Implementation
// =============================

object DummyDependencies : Dependencies {
    // setup dummy dependencies

    override val checkProductExists = CheckProductCodeExists { productCode -> true }

    override val checkAddressExists =
        CheckAddressExists { unvalidatedAddress -> CheckedAddress(unvalidatedAddress).right() }

    private val getStandardPrices = GetStandardPrices { GetProductPrice { productCode -> Price.unsafeCreate(10.0) } }

    private val getPromotionPrices: (PromotionCode) -> TryGetProductPrice = { promotionCode: PromotionCode ->

        when (promotionCode.value) {
            "HALF" ->
                TryGetProductPrice { productCode -> if (productCode.text == "ONSALE") Price.unsafeCreate(5.0) else null }

            "QUARTER" ->
                TryGetProductPrice { productCode -> if (productCode.text == "ONSALE") Price.unsafeCreate(2.5) else null }

            else ->
                TryGetProductPrice { productCode -> null }
        }
    }

    override val getPricingFunction: GetPricingFunction =
        Pricing.getPricingFunction(getStandardPrices, getPromotionPrices)

    override val calculateShippingCost =
        DefaultImplementation.calculateShippingCost

    override val createOrderAcknowledgmentLetter =
        CreateOrderAcknowledgmentLetter { pricedOrder -> HtmlString("some text") }

    override val sendOrderAcknowledgment =
        SendOrderAcknowledgment { orderAcknowledgement -> SendResult.Sent }
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
            val json = serializeJson(dto)
            HttpResponse(httpStatusCode = 401, body = json)
        },
        { events ->
            // turn domain events into dtos
            val dtos = events.map(PlaceOrderEventDto::fromDomain)
            // and serialize to JSON
            val json = serializeJson(dtos)
            HttpResponse(httpStatusCode = 200, body = json)
        },
    )
}

val placeOrderApiWithDummyDependencies =
    with(DummyDependencies) {
        placeOrderApi()
    }

context (Dependencies)
fun placeOrderApi() = PlaceOrderApi { request ->
    // following the approach in "A Complete Serialization Pipeline" in chapter 11

    // start with a string
    val orderFormJson = request.body
    val orderForm = deserializeJson(OrderFormDto.serializer(), orderFormJson)
    // convert to domain object
    val unvalidatedOrder = orderForm.let(OrderFormDto::toUnvalidatedOrder)

    // setup the dependencies. See "Injecting Dependencies" in chapter 9
    val workflow = placeOrder()

    // now we are in the pure domain
    val asyncResult = workflow(unvalidatedOrder)

    // now convert from the pure domain back to a HttpResponse
    asyncResult.let(workflowResultToHttpReponse)
}

suspend fun main() {
//    val dto = OrderFormDto(
//        "orderId",
//        CustomerInfoDto("firstName", "lastName", "emailAddress", "vipStatus"),
//        AddressDto("addr1", "addr2", "addr3", "addr4", "city", "zip", "state", "country"),
//        AddressDto("addr1", "addr2", "addr3", "addr4", "city", "zip", "state", "country"),
//        listOf(AddressDto.OrderFormLineDto("orderLineId", "productCode", 1.0)),
//        "promotionCode",
//    )
//    val j = Json .encodeToString(OrderFormDto.serializer(), dto)
//    println(j)
//    val o = Json.decodeFromString(OrderFormDto.serializer(), j)
//    println(o)
    val json =
        """{"orderId":"orderId","customerInfo":{"firstName":"firstName","lastName":"lastName","emailAddress":"email@Address","vipStatus":"VIP"},"shippingAddress":{"addressLine1":"addr1","addressLine2":"addr2","addressLine3":"addr3","addressLine4":"addr4","city":"city","zipCode":"12345","state":"CA","country":"country"},"billingAddress":{"addressLine1":"addr1","addressLine2":"addr2","addressLine3":"addr3","addressLine4":"addr4","city":"city","zipCode":"12345","state":"CA","country":"country"},"lines":[{"orderLineId":"orderLineId","productCode":"W1234","quantity":1.0}],"promotionCode":"promotionCode"}"""
    val result =
        placeOrderApiWithDummyDependencies(HttpRequest("", "", json))
    println(result)
}
