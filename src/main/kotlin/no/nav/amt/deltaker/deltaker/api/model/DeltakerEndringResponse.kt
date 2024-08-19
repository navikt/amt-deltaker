package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.Deltakelsesinnhold
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class DeltakerEndringResponse(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: Deltakelsesinnhold,
    val status: DeltakerStatus,
    val historikk: List<DeltakerHistorikk>,
)

fun Deltaker.toDeltakerEndringResponse(historikk: List<DeltakerHistorikk>) = DeltakerEndringResponse(
    id = id,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    innhold = deltakelsesinnhold,
    status = status,
    historikk = historikk,
)
