package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadMapperService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

class DeltakerProducerService(
    private val deltakerKafkaPayloadMapperService: DeltakerKafkaPayloadMapperService,
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
        val deltakerPayload = deltakerKafkaPayloadMapperService.tilDeltakerPayload(deltaker, forcedUpdate)

        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)) {
            if (publiserTilDeltakerV1) {
                deltakerV1Producer.produce(deltakerPayload.v1)
            }
        }

        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode) ||
            unleashToggle.skalLeseArenaDataForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)
        ) {
            deltakerProducer.produce(deltakerPayload.v2)
        }
    }

    fun tombstone(deltakerId: UUID) {
        deltakerProducer.produceTombstone(deltakerId)
        deltakerV1Producer.produceTombstone(deltakerId)
    }
}
