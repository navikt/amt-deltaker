package no.nav.amt.deltaker.hendelse.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = HendelseEndring.EndreStartdato::class, name = "EndreStartdato"),
    JsonSubTypes.Type(value = HendelseEndring.EndreSluttdato::class, name = "EndreSluttdato"),
    JsonSubTypes.Type(value = HendelseEndring.EndreDeltakelsesmengde::class, name = "EndreDeltakelsesmengde"),
    JsonSubTypes.Type(value = HendelseEndring.EndreBakgrunnsinformasjon::class, name = "EndreBakgrunnsinformasjon"),
    JsonSubTypes.Type(value = HendelseEndring.EndreInnhold::class, name = "EndreInnhold"),
    JsonSubTypes.Type(value = HendelseEndring.ForlengDeltakelse::class, name = "ForlengDeltakelse"),
    JsonSubTypes.Type(value = HendelseEndring.EndreSluttarsak::class, name = "EndreSluttarsak"),
    JsonSubTypes.Type(value = HendelseEndring.OpprettUtkast::class, name = "OpprettUtkast"),
    JsonSubTypes.Type(value = HendelseEndring.AvbrytUtkast::class, name = "AvbrytUtkast"),
    JsonSubTypes.Type(value = HendelseEndring.AvsluttDeltakelse::class, name = "AvsluttDeltakelse"),
    JsonSubTypes.Type(value = HendelseEndring.IkkeAktuell::class, name = "IkkeAktuell"),
    JsonSubTypes.Type(value = HendelseEndring.InnbyggerGodkjennUtkast::class, name = "InnbyggerGodkjennUtkast"),
    JsonSubTypes.Type(value = HendelseEndring.NavGodkjennUtkast::class, name = "NavGodkjennUtkast"),
)
sealed interface HendelseEndring {
    data class OpprettUtkast(
        val utkast: UtkastDto,
    ) : HendelseEndring

    data class AvbrytUtkast(
        val utkast: UtkastDto,
    ) : HendelseEndring

    data class InnbyggerGodkjennUtkast(
        val utkast: UtkastDto,
    ) : HendelseEndring

    data class NavGodkjennUtkast(
        val utkast: UtkastDto,
    ) : HendelseEndring

    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
    ) : HendelseEndring

    data class EndreInnhold(
        val innhold: List<InnholdDto>,
    ) : HendelseEndring

    data class EndreDeltakelsesmengde(
        val deltakelsesprosent: Float?,
        val dagerPerUke: Float?,
    ) : HendelseEndring

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate? = null,
    ) : HendelseEndring

    data class EndreSluttdato(
        val sluttdato: LocalDate,
    ) : HendelseEndring

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
    ) : HendelseEndring

    data class IkkeAktuell(
        val aarsak: DeltakerEndring.Aarsak,
    ) : HendelseEndring

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndring.Aarsak,
        val sluttdato: LocalDate,
    ) : HendelseEndring

    data class EndreSluttarsak(
        val aarsak: DeltakerEndring.Aarsak,
    ) : HendelseEndring
}

data class UtkastDto(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: List<InnholdDto>,
)

data class InnholdDto(
    val tekst: String,
    val innholdskode: String,
    val beskrivelse: String?,
)

fun DeltakerEndring.toHendelseEndring() = when (endring) {
    is DeltakerEndring.Endring.AvsluttDeltakelse -> HendelseEndring.AvsluttDeltakelse(
        endring.aarsak,
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> HendelseEndring.EndreBakgrunnsinformasjon(
        endring.bakgrunnsinformasjon,
    )

    is DeltakerEndring.Endring.EndreDeltakelsesmengde -> HendelseEndring.EndreDeltakelsesmengde(
        endring.deltakelsesprosent,
        endring.dagerPerUke,
    )

    is DeltakerEndring.Endring.EndreInnhold -> HendelseEndring.EndreInnhold(
        endring.innhold.map { InnholdDto(it.tekst, it.innholdskode, it.beskrivelse) },
    )

    is DeltakerEndring.Endring.EndreSluttarsak -> HendelseEndring.EndreSluttarsak(
        endring.aarsak,
    )

    is DeltakerEndring.Endring.EndreSluttdato -> HendelseEndring.EndreSluttdato(
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.EndreStartdato -> HendelseEndring.EndreStartdato(
        endring.startdato,
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.ForlengDeltakelse -> HendelseEndring.ForlengDeltakelse(
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.IkkeAktuell -> HendelseEndring.IkkeAktuell(
        endring.aarsak,
    )
}
