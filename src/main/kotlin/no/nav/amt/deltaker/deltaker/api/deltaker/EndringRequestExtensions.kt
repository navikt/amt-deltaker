package no.nav.amt.deltaker.deltaker.api.deltaker

import no.nav.amt.deltaker.deltaker.api.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.EndreAvslutningRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.EndringForslagRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.EndringRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.deltaker.request.StartdatoRequest
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.util.UUID

fun EndringRequest.getForslagId(): UUID? = if (this is EndringForslagRequest) {
    this.forslagId
} else {
    null
}

fun EndringRequest.toDeltakerEndringEndring(): DeltakerEndring.Endring = when (this) {
    is SluttdatoRequest -> DeltakerEndring.Endring.EndreSluttdato(this.sluttdato, this.begrunnelse)
    is SluttarsakRequest -> DeltakerEndring.Endring.EndreSluttarsak(this.aarsak, this.begrunnelse)
    is ForlengDeltakelseRequest -> DeltakerEndring.Endring.ForlengDeltakelse(this.sluttdato, this.begrunnelse)
    is IkkeAktuellRequest -> DeltakerEndring.Endring.IkkeAktuell(this.aarsak, this.begrunnelse)
    is AvsluttDeltakelseRequest -> DeltakerEndring.Endring.AvsluttDeltakelse(
        this.aarsak,
        this.sluttdato,
        this.begrunnelse,
    )
    is EndreAvslutningRequest -> DeltakerEndring.Endring.EndreAvslutning(this.aarsak, this.harFullfort, this.begrunnelse)
    is AvbrytDeltakelseRequest -> DeltakerEndring.Endring.AvbrytDeltakelse(
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

    is FjernOppstartsdatoRequest -> DeltakerEndring.Endring.FjernOppstartsdato(
        begrunnelse = this.begrunnelse,
    )
}
