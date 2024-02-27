package no.nav.amt.deltaker.deltaker.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerEndring(
    val id: UUID,
    val deltakerId: UUID,
    val endringstype: Endringstype,
    val endring: Endring,
    val endretAv: UUID,
    val endretAvEnhet: UUID,
    val endret: LocalDateTime,
) {
    enum class Endringstype {
        STARTDATO, SLUTTDATO, DELTAKELSESMENGDE, BAKGRUNNSINFORMASJON, INNHOLD, IKKE_AKTUELL, FORLENGELSE, AVSLUTT_DELTAKELSE, SLUTTARSAK
    }

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
            SYK, FATT_JOBB, TRENGER_ANNEN_STOTTE, UTDANNING, IKKE_MOTT, ANNET
        }

        fun toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
            DeltakerStatus.Aarsak.Type.valueOf(type.name),
            beskrivelse,
        )
    }

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
        ) : Endring()

        data class EndreStartdato(
            val startdato: LocalDate?,
        ) : Endring()

        data class EndreSluttdato(
            val sluttdato: LocalDate,
        ) : Endring()

        data class ForlengDeltakelse(
            val sluttdato: LocalDate,
        ) : Endring()

        data class IkkeAktuell(
            val aarsak: Aarsak?,
        ) : Endring()

        data class AvsluttDeltakelse(
            val aarsak: Aarsak,
            val sluttdato: LocalDate,
        ) : Endring()

        data class EndreSluttarsak(
            val aarsak: Aarsak,
        ) : Endring()
    }
}
