package no.nav.amt.deltaker.hendelse.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = HendelseType.EndreStartdato::class, name = "EndreStartdato"),
    JsonSubTypes.Type(value = HendelseType.EndreSluttdato::class, name = "EndreSluttdato"),
    JsonSubTypes.Type(value = HendelseType.EndreDeltakelsesmengde::class, name = "EndreDeltakelsesmengde"),
    JsonSubTypes.Type(value = HendelseType.EndreBakgrunnsinformasjon::class, name = "EndreBakgrunnsinformasjon"),
    JsonSubTypes.Type(value = HendelseType.EndreInnhold::class, name = "EndreInnhold"),
    JsonSubTypes.Type(value = HendelseType.ForlengDeltakelse::class, name = "ForlengDeltakelse"),
    JsonSubTypes.Type(value = HendelseType.EndreSluttarsak::class, name = "EndreSluttarsak"),
    JsonSubTypes.Type(value = HendelseType.OpprettUtkast::class, name = "OpprettUtkast"),
    JsonSubTypes.Type(value = HendelseType.EndreUtkast::class, name = "EndreUtkast"),
    JsonSubTypes.Type(value = HendelseType.AvbrytUtkast::class, name = "AvbrytUtkast"),
    JsonSubTypes.Type(value = HendelseType.AvsluttDeltakelse::class, name = "AvsluttDeltakelse"),
    JsonSubTypes.Type(value = HendelseType.IkkeAktuell::class, name = "IkkeAktuell"),
    JsonSubTypes.Type(value = HendelseType.InnbyggerGodkjennUtkast::class, name = "InnbyggerGodkjennUtkast"),
    JsonSubTypes.Type(value = HendelseType.NavGodkjennUtkast::class, name = "NavGodkjennUtkast"),
)
sealed interface HendelseType {
    data class OpprettUtkast(
        val utkast: UtkastDto,
    ) : HendelseType

    data class EndreUtkast(
        val utkast: UtkastDto,
    ) : HendelseType

    data class AvbrytUtkast(
        val utkast: UtkastDto,
    ) : HendelseType

    data class InnbyggerGodkjennUtkast(
        val utkast: UtkastDto,
    ) : HendelseType

    data class NavGodkjennUtkast(
        val utkast: UtkastDto,
    ) : HendelseType

    data class EndreBakgrunnsinformasjon(
        val bakgrunnsinformasjon: String?,
    ) : HendelseType

    data class EndreInnhold(
        val innhold: List<InnholdDto>,
    ) : HendelseType

    data class EndreDeltakelsesmengde(
        val deltakelsesprosent: Float?,
        val dagerPerUke: Float?,
    ) : HendelseType

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate? = null,
    ) : HendelseType

    data class EndreSluttdato(
        val sluttdato: LocalDate,
    ) : HendelseType

    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
    ) : HendelseType

    data class IkkeAktuell(
        val aarsak: DeltakerEndring.Aarsak,
    ) : HendelseType

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndring.Aarsak,
        val sluttdato: LocalDate,
    ) : HendelseType

    data class EndreSluttarsak(
        val aarsak: DeltakerEndring.Aarsak,
    ) : HendelseType
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
    is DeltakerEndring.Endring.AvsluttDeltakelse -> HendelseType.AvsluttDeltakelse(
        endring.aarsak,
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> HendelseType.EndreBakgrunnsinformasjon(
        endring.bakgrunnsinformasjon,
    )

    is DeltakerEndring.Endring.EndreDeltakelsesmengde -> HendelseType.EndreDeltakelsesmengde(
        endring.deltakelsesprosent,
        endring.dagerPerUke,
    )

    is DeltakerEndring.Endring.EndreInnhold -> HendelseType.EndreInnhold(
        endring.innhold.map { InnholdDto(it.tekst, it.innholdskode, it.beskrivelse) },
    )

    is DeltakerEndring.Endring.EndreSluttarsak -> HendelseType.EndreSluttarsak(
        endring.aarsak,
    )

    is DeltakerEndring.Endring.EndreSluttdato -> HendelseType.EndreSluttdato(
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.EndreStartdato -> HendelseType.EndreStartdato(
        endring.startdato,
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.ForlengDeltakelse -> HendelseType.ForlengDeltakelse(
        endring.sluttdato,
    )

    is DeltakerEndring.Endring.IkkeAktuell -> HendelseType.IkkeAktuell(
        endring.aarsak,
    )
}
