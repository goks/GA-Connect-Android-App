// 1️⃣ Brochure Data Model
package com.example.pricelist.data

data class Brochure(
    val id:    String        = "",
    val name:  String        = "",
    val file:  String        = "",   // file name in Storage, e.g. "sink_catalogue.pdf"
    val contentType: String  = "application/pdf",
    val description: String  = "",
    val brochureDate: String = "",
    val ts:    Long          = 0L    // lastUpdated epoch‐ms (optional)
)
