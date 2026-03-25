package com.example.scannerone.nominatimApi

import com.google.gson.annotations.SerializedName

data class NominatimResponse(
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("address")      val address: AddressDetail?,
    @SerializedName("extratags")    val extratags: Map<String, String>?
)

data class AddressDetail(
    @SerializedName("road")         val road: String?,
    @SerializedName("house_number") val houseNumber: String?,
    @SerializedName("city")         val city: String?,
    @SerializedName("town")         val town: String?,
    @SerializedName("village")      val village: String?,
    @SerializedName("state")        val state: String?,
    @SerializedName("postcode")     val postcode: String?,
    @SerializedName("country")      val country: String?
) {
    // city può essere null su OSM, questo evita null check ovunque
    val resolvedCity: String?
        get() = city ?: town ?: village
}

fun NominatimResponse.toWifiNetworkFields(): Map<String, String?> {
    val a = this.address
    
    // Controlliamo se la via sembra un codice interno tecnico (solo numeri e underscore)
    val hasUglyRoadName = a?.road?.matches(Regex("^[0-9_]+$")) == true
    val cleanRoad = if (hasUglyRoadName) null else a?.road

    return mapOf(
        "realStreet"  to listOfNotNull(cleanRoad, a?.houseNumber).joinToString(" ").ifEmpty { null },
        "realCity"    to (a?.city ?: a?.town ?: a?.village),
        "realRegion"  to a?.state,      
        "realCountry" to a?.country
    )
}