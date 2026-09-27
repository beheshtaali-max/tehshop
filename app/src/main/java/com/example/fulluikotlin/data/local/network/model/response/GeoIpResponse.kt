package com.example.fulluikotlin.data.local.network.model.response

import kotlinx.serialization.Serializable

@Serializable
data class GeoIpResponse(
    val city: String?,
    val organization: String?,
    val postal_code: String?,
    val isp: String?,
    val latitude: Double,
    val ip: String,
    val continent_code: String?,
    val asn_organization: String?,
    val country: String?,
    val asn: Int?,
    val country_code: String?,
    val offset: Int?,
    val region: String?,
    val timezone: String?,
    val region_code: String?,
    val longitude: Double
)