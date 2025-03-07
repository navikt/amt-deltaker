package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.kafka.dto.DeltakerDtoMapperService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

class DeltakerProducerService(
    private val deltakerDtoMapperService: DeltakerDtoMapperService,
    private val deltakerProducer: DeltakerProducer,
    private val deltakerV1Producer: DeltakerV1Producer,
    private val unleashToggle: UnleashToggle,
) {
    suspend fun produce(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        publiserTilDeltakerV1: Boolean = true,
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) return
        val deltakerDto = deltakerDtoMapperService.tilDeltakerDto(deltaker, forcedUpdate)

        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.arenaKode)) {
            deltakerProducer.produce(deltakerDto.v2)
            if (publiserTilDeltakerV1) {
                deltakerV1Producer.produce(deltakerDto.v1)
            }
        }
    }

    fun tombstone(deltakerId: UUID) {
        deltakerProducer.produceTombstone(deltakerId)
        deltakerV1Producer.produceTombstone(deltakerId)
    }
}
