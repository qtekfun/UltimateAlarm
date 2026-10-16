package com.qtekfun.ultimatealarm.settings

/**
 * Countries offered in Settings → Holidays (any Nager.Date country works; these are the ones listed) and Spain's
 * autonomous communities with their ISO 3166-2 codes (RF-F1). Names of countries come from the system locale.
 */
internal object Regions {
    val countries = listOf("ES", "PT", "FR", "IT", "DE", "GB", "IE", "NL", "BE", "AT", "CH", "US", "MX", "AR", "CO", "CL", "PE")

    val spain = listOf(
        "ES-AN" to "Andalucía",
        "ES-AR" to "Aragón",
        "ES-AS" to "Asturias",
        "ES-IB" to "Illes Balears",
        "ES-CN" to "Canarias",
        "ES-CB" to "Cantabria",
        "ES-CL" to "Castilla y León",
        "ES-CM" to "Castilla-La Mancha",
        "ES-CT" to "Catalunya",
        "ES-CE" to "Ceuta",
        "ES-EX" to "Extremadura",
        "ES-GA" to "Galicia",
        "ES-RI" to "La Rioja",
        "ES-MD" to "Comunidad de Madrid",
        "ES-ML" to "Melilla",
        "ES-MC" to "Región de Murcia",
        "ES-NC" to "Navarra",
        "ES-PV" to "País Vasco",
        "ES-VC" to "Comunitat Valenciana"
    )
}
