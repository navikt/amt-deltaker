package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerProducer: DeltakerProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) =
        deltakerRepository.getMany(personident, deltakerlisteId)

    suspend fun upsertDeltaker(oppdatertDeltaker: Deltaker) {
        deltakerRepository.upsert(oppdatertDeltaker)
        if (oppdatertDeltaker.status.type != DeltakerStatus.Type.KLADD) {
            deltakerProducer.produce(get(oppdatertDeltaker.id).getOrThrow())
        }
        log.info("Oppdatert deltaker med id ${oppdatertDeltaker.id}")
    }

    fun delete(deltakerId: UUID) {
        deltakerRepository.deleteDeltakerOgStatus(deltakerId)
    }

    suspend fun upsertEndretDeltaker(deltakerId: UUID, request: EndringRequest) {
        val deltaker = get(deltakerId).getOrThrow()

        deltakerEndringService
            .upsertEndring(deltaker, request)
            .onSuccess { endretDeltaker -> upsertDeltaker(endretDeltaker) }
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
