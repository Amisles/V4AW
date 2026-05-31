package org.amisles.v4aw.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SearchEndpoint(
    val actionUrl: String = "",
    val method: String = "GET",
    val queryParam: String = "q",
    val extraParams: Map<String, String> = emptyMap(),
    val placeholder: String? = null,
    val sourceUrl: String = ""
)
