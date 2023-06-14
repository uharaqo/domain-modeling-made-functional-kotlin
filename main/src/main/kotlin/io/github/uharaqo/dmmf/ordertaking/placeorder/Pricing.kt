package io.github.uharaqo.dmmf.ordertaking.placeorder

import io.github.uharaqo.dmmf.ordertaking.common.*
import io.github.uharaqo.dmmf.ordertaking.placeorder.implementation.*

// Move all procing logic into its own module,
// as it will likely get complicated!

// An internal helper module to help with pricing
object Pricing {

    // Create a pricing method given a promotionCode on the unvalidated order form
    // If null -> Standard otherwise wrap in PromotionCode
    fun createPricingMethod(promotionCode: String?): PricingMethod =
        when {
            promotionCode.isNullOrBlank() -> PricingMethod.Standard
            else -> PricingMethod.Promotion(PromotionCode(promotionCode))
        }

    fun getPricingFunction(standardPrices: GetStandardPrices, promoPrices: GetPromotionPrices): GetPricingFunction {
        // cache the standard prices
        val getStandardPrice: GetProductPrice = standardPrices()

        // the promotional pricing function
        val getPromotionPrice = { promotionCode: PromotionCode ->
            // cache the promotional prices
            val getPromotionPrice = promoPrices(promotionCode)
            // return the lookup function
            GetProductPrice { productCode -> getPromotionPrice(productCode) ?: getStandardPrice(productCode) }
        }

        // return a function that conforms to GetPricingFunction
        return GetPricingFunction { pricingMethod ->
            when (pricingMethod) {
                PricingMethod.Standard -> getStandardPrice
                is PricingMethod.Promotion -> getPromotionPrice(pricingMethod.value)
            }
        }
    }
}
