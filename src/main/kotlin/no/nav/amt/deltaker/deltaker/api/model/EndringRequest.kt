package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String
}

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}

data class BakgrunnsinformasjonRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val bakgrunnsinformasjon: String?,
) : EndringRequest

data class InnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val deltakelsesinnhold: Deltakelsesinnhold,
) : EndringRequest

data class DeltakelsesmengdeRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
    val gyldigFra: LocalDate?,
) : EndringForslagRequest

data class StartdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
    val begrunnelse: String?,
) : EndringForslagRequest

data class SluttdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val begrunnelse: String?,
) : EndringForslagRequest

data class SluttarsakRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest

data class ForlengDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val begrunnelse: String?,
) : EndringForslagRequest

data class IkkeAktuellRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest

data class AvsluttDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest

data class ReaktiverDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val begrunnelse: String,
) : EndringRequest

fun EndringRequest.getForslagId(): UUID? = if (this is EndringForslagRequest) {
    this.forslagId
} else {
    null
}

fun EndringRequest.toDeltakerEndringEndring() = when (this) {
    is SluttdatoRequest -> DeltakerEndring.Endring.EndreSluttdato(this.sluttdato, this.begrunnelse)
    is SluttarsakRequest -> DeltakerEndring.Endring.EndreSluttarsak(this.aarsak, this.begrunnelse)
    is ForlengDeltakelseRequest -> DeltakerEndring.Endring.ForlengDeltakelse(this.sluttdato, this.begrunnelse)
    is IkkeAktuellRequest -> DeltakerEndring.Endring.IkkeAktuell(this.aarsak, this.begrunnelse)
    is AvsluttDeltakelseRequest -> DeltakerEndring.Endring.AvsluttDeltakelse(
        this.aarsak,
        this.sluttdato,
        this.begrunnelse,
    )

    is ReaktiverDeltakelseRequest -> DeltakerEndring.Endring.ReaktiverDeltakelse(
        LocalDate.now(),
        this.begrunnelse,
    )

    is BakgrunnsinformasjonRequest -> DeltakerEndring.Endring.EndreBakgrunnsinformasjon(this.bakgrunnsinformasjon)
    is InnholdRequest -> DeltakerEndring.Endring.EndreInnhold(
        this.deltakelsesinnhold.ledetekst,
        this.deltakelsesinnhold.innhold,
    )

    is DeltakelsesmengdeRequest -> DeltakerEndring.Endring.EndreDeltakelsesmengde(
        deltakelsesprosent = this.deltakelsesprosent?.toFloat(),
        dagerPerUke = this.dagerPerUke?.toFloat(),
        begrunnelse = this.begrunnelse,
        gyldigFra = this.gyldigFra,
    )

    is StartdatoRequest ->
        DeltakerEndring.Endring.EndreStartdato(
            startdato = this.startdato,
            sluttdato = this.sluttdato,
            this.begrunnelse,
        )
}
