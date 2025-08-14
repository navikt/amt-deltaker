package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.api.model.response.DeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.api.model.response.OpprettKladdResponse
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk

fun Deltaker.toDeltakerEndringResponse(historikk: List<DeltakerHistorikk>) = DeltakerEndringResponse(
    id = id,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    deltakelsesinnhold = deltakelsesinnhold,
    status = status,
    historikk = historikk,
)

fun Deltaker.toKladdResponse(): OpprettKladdResponse = OpprettKladdResponse(
    id = id,
    navBruker = navBruker,
    deltakerlisteId = deltakerliste.id,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    deltakelsesinnhold = deltakelsesinnhold ?: throw IllegalStateException("Kladd mangler obligatorisk innhold(deltakelsesinnhold)"),
    status = status,
)
