package no.nav.amt.deltaker.navbruker.model

import java.util.UUID

data class NavBruker(
    val personId: UUID,
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navVeilederId: UUID?,
    val navEnhetId: UUID?,
    val telefon: String?,
    val epost: String?,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
)

enum class Adressebeskyttelse {
    STRENGT_FORTROLIG,
    FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
}
