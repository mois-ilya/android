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

package io.tonapi.models

import io.tonapi.models.PoolImplementationType

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param address 
 * @param name 
 * @param totalAmount 
 * @param implementation 
 * @param apy APY in percent
 * @param minStake 
 * @param cycleStart current nomination cycle beginning timestamp
 * @param cycleEnd current nomination cycle ending timestamp
 * @param verified this pool has verified source code or managed by trusted company
 * @param currentNominators current number of nominators
 * @param maxNominators maximum number of nominators
 * @param nominatorsStake total stake of all nominators
 * @param validatorStake stake of validator
 * @param liquidJettonMaster for liquid staking master account of jetton
 * @param cycleLength 
 */


data class PoolInfo (

    @Json(name = "address")
    val address: kotlin.String,

    @Json(name = "name")
    val name: kotlin.String,

    @Json(name = "total_amount")
    val totalAmount: kotlin.Long,

    @Json(name = "implementation")
    val implementation: PoolImplementationType,

    /* APY in percent */
    @Json(name = "apy")
    val apy: java.math.BigDecimal,

    @Json(name = "min_stake")
    val minStake: kotlin.Long,

    /* current nomination cycle beginning timestamp */
    @Json(name = "cycle_start")
    val cycleStart: kotlin.Long,

    /* current nomination cycle ending timestamp */
    @Json(name = "cycle_end")
    val cycleEnd: kotlin.Long,

    /* this pool has verified source code or managed by trusted company */
    @Json(name = "verified")
    val verified: kotlin.Boolean,

    /* current number of nominators */
    @Json(name = "current_nominators")
    val currentNominators: kotlin.Int,

    /* maximum number of nominators */
    @Json(name = "max_nominators")
    val maxNominators: kotlin.Int,

    /* total stake of all nominators */
    @Json(name = "nominators_stake")
    val nominatorsStake: kotlin.Long,

    /* stake of validator */
    @Json(name = "validator_stake")
    val validatorStake: kotlin.Long,

    /* for liquid staking master account of jetton */
    @Json(name = "liquid_jetton_master")
    val liquidJettonMaster: kotlin.String? = null,

    @Json(name = "cycle_length")
    val cycleLength: kotlin.Long? = null

)
