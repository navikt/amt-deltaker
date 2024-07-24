package no.nav.amt.deltaker.deltaker.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerEndring(
    val id: UUID,
    val deltakerId: UUID,
    val endring: Endring,
    val endretAv: UUID,
    val endretAvEnhet: UUID,
    val endret: LocalDateTime,
    val forslag: Forslag?,
) {
    data class Aarsak(
        val type: Type,
        val beskrivelse: String? = null,
    ) {
        init {
            if (beskrivelse != null && type != Type.ANNET) {
                error("Aarsak $type skal ikke ha beskrivelse")
            }
        }

        enum class Type {
            SYK,
            FATT_JOBB,
            TRENGER_ANNEN_STOTTE,
            UTDANNING,
            IKKE_MOTT,
            ANNET,
        }

        fun toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
            DeltakerStatus.Aarsak.Type.valueOf(type.name),
            beskrivelse,
        )
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Endring.EndreStartdato::class, name = "EndreStartdato"),
        JsonSubTypes.Type(value = Endring.EndreSluttdato::class, name = "EndreSluttdato"),
        JsonSubTypes.Type(value = Endring.EndreDeltakelsesmengde::class, name = "EndreDeltakelsesmengde"),
        JsonSubTypes.Type(value = Endring.EndreBakgrunnsinformasjon::class, name = "EndreBakgrunnsinformasjon"),
        JsonSubTypes.Type(value = Endring.EndreInnhold::class, name = "EndreInnhold"),
        JsonSubTypes.Type(value = Endring.IkkeAktuell::class, name = "IkkeAktuell"),
        JsonSubTypes.Type(value = Endring.ForlengDeltakelse::class, name = "ForlengDeltakelse"),
        JsonSubTypes.Type(value = Endring.AvsluttDeltakelse::class, name = "AvsluttDeltakelse"),
        JsonSubTypes.Type(value = Endring.EndreSluttarsak::class, name = "EndreSluttarsak"),
        JsonSubTypes.Type(value = Endring.ReaktiverDeltakelse::class, name = "ReaktiverDeltakelse"),
    )
    sealed class Endring {
        data class EndreBakgrunnsinformasjon(
            val bakgrunnsinformasjon: String?,
        ) : Endring()

        data class EndreInnhold(
            val innhold: List<Innhold>,
        ) : Endring()

        data class EndreDeltakelsesmengde(
            val deltakelsesprosent: Float?,
            val dagerPerUke: Float?,
            val begrunnelse: String?,
        ) : Endring()

        data class EndreStartdato(
            val startdato: LocalDate?,
            val sluttdato: LocalDate? = null,
        ) : Endring()

        data class EndreSluttdato(
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring()

        data class ForlengDeltakelse(
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring()

        data class IkkeAktuell(
            val aarsak: Aarsak,
            val begrunnelse: String?,
        ) : Endring()

        data class AvsluttDeltakelse(
            val aarsak: Aarsak,
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring()

        data class EndreSluttarsak(
            val aarsak: Aarsak,
        ) : Endring()

        data class ReaktiverDeltakelse(
            val reaktivertDato: LocalDate,
            val begrunnelse: String,
        ) : Endring()
    }
}
