package no.nav.amt.deltaker.deltaker.kafka.dto

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class EnkeltplassDeltakerPayload(
    val id: UUID,
    val gjennomforingId: UUID,
    val personIdent: String,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: DeltakerStatus.Type,
    val statusAarsak: DeltakerStatus.Aarsak?,
    val dagerPerUke: Float?,
    val prosentDeltid: Float?,
    val registrertDato: LocalDateTime,
    val statusEndretDato: LocalDateTime?,
    val innsokBegrunnelse: String?,
)
