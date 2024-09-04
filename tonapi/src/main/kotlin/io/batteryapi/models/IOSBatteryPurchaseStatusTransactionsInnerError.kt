/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package io.batteryapi.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param msg 
 * @param code 
 */


data class IOSBatteryPurchaseStatusTransactionsInnerError (

    @Json(name = "msg")
    val msg: kotlin.String,

    @Json(name = "code")
    val code: IOSBatteryPurchaseStatusTransactionsInnerError.Code

) {

    /**
     * 
     *
     * Values: invalidMinusBundleMinusId,invalidMinusProductMinusId,userMinusNotMinusFound,purchaseMinusIsMinusAlreadyMinusUsed,temporaryMinusError,unknown
     */
    @JsonClass(generateAdapter = false)
    enum class Code(val value: kotlin.String) {
        @Json(name = "invalid-bundle-id") invalidMinusBundleMinusId("invalid-bundle-id"),
        @Json(name = "invalid-product-id") invalidMinusProductMinusId("invalid-product-id"),
        @Json(name = "user-not-found") userMinusNotMinusFound("user-not-found"),
        @Json(name = "purchase-is-already-used") purchaseMinusIsMinusAlreadyMinusUsed("purchase-is-already-used"),
        @Json(name = "temporary-error") temporaryMinusError("temporary-error"),
        @Json(name = "unknown") unknown("unknown");
    }

}
