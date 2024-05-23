package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.hendelse.HendelseService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerProducer: DeltakerProducer,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) = deltakerRepository.getMany(personident, deltakerlisteId)

    fun getDeltakelser(personident: String) = deltakerRepository.getMany(personident)

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

    suspend fun upsertEndretDeltaker(deltakerId: UUID, request: EndringRequest): Deltaker {
        val deltaker = get(deltakerId).getOrThrow()

        return deltakerEndringService.upsertEndring(deltaker, request).fold(
            onSuccess = { endretDeltaker ->
                upsertDeltaker(endretDeltaker)
                return@fold get(deltakerId).getOrThrow()
            },
            onFailure = {
                return@fold deltaker
            },
        )
    }

    suspend fun produserDeltakereForPerson(personident: String) {
        getDeltakelser(personident).forEach {
            deltakerProducer.produce(it)
        }
    }

    suspend fun fattVedtak(deltakerId: UUID, vedtakId: UUID): Deltaker {
        val deltaker = get(deltakerId).getOrThrow().let {
            if (it.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                it.copy(status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            } else {
                it
            }
        }

        vedtakService.fattVedtak(vedtakId, deltaker)

        upsertDeltaker(deltaker)
        return deltaker
    }

    suspend fun oppdaterSistBesokt(deltakerId: UUID, sistBesokt: LocalDateTime) {
        deltakerRepository.oppdaterSistBesokt(deltakerId, sistBesokt)

        val deltaker = get(deltakerId).getOrThrow()
        hendelseService.hendelseForSistBesokt(deltaker, sistBesokt)
    }
}

fun nyDeltakerStatus(
    type: DeltakerStatus.Type,
    aarsak: DeltakerStatus.Aarsak? = null,
    gyldigFra: LocalDateTime = LocalDateTime.now(),
) = DeltakerStatus(
    id = UUID.randomUUID(),
    type = type,
    aarsak = aarsak,
    gyldigFra = gyldigFra,
    gyldigTil = null,
    opprettet = LocalDateTime.now(),
)
