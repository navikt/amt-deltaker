package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) =
        deltakerRepository.getMany(personident, deltakerlisteId)

    fun lagreKladd(
        deltaker: Deltaker,
    ) {
        deltakerRepository.upsert(deltaker)
        log.info("Lagret kladd for deltaker med id ${deltaker.id}")
    }
}

fun nyDeltakerStatus(type: DeltakerStatus.Type, aarsak: DeltakerStatus.Aarsak? = null) = DeltakerStatus(
    id = UUID.randomUUID(),
    type = type,
    aarsak = aarsak,
    gyldigFra = LocalDateTime.now(),
    gyldigTil = null,
    opprettet = LocalDateTime.now(),
)
