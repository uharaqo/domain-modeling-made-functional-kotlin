package io.github.uharaqo.dmmf.ordertaking.placeorder.api

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

context (PlaceOrderDependencies)
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
