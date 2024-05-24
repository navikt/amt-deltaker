package no.nav.amt.deltaker.hendelse.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = HendelseAnsvarlig.NavVeileder::class, name = "NavVeileder"),
    JsonSubTypes.Type(value = HendelseAnsvarlig.Deltaker::class, name = "Deltaker"),
)
sealed interface HendelseAnsvarlig {
    val navn: String

    data class NavVeileder(
        val id: UUID,
        override val navn: String,
        val navIdent: String,
        val enhet: Enhet,
    ) : HendelseAnsvarlig {
        data class Enhet(
            val id: UUID,
            val enhetsnummer: String,
        )
    }

    data class Deltaker(
        val id: UUID,
        override val navn: String,
    ) : HendelseAnsvarlig
}
