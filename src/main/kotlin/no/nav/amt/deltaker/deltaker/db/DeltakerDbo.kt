package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import java.time.LocalDate
import java.util.UUID

data class DeltakerDbo(
    val id: UUID,
    val personId: UUID,
    val deltakerlisteId: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
)
