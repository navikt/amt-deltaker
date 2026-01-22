package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

class DeltakerProducerService(
    private val deltakerKafkaPayloadBuilder: DeltakerKafkaPayloadBuilder,
    private val deltakerProducer: DeltakerProducer,
    private val deltakerV1Producer: DeltakerV1Producer,
    private val unleashToggle: UnleashToggle,
) {
    fun produce(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        publiserTilDeltakerV1: Boolean = true,
        publiserTilDeltakerV2: Boolean = true,
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) return

        if (publiserTilDeltakerV1) {
            produceDeltakerV1Topic(deltaker)
        }
        if (publiserTilDeltakerV2) {
            produceDeltakerV2Topic(deltaker, forcedUpdate)
        }
    }

    fun produceDeltakerV1Topic(deltaker: Deltaker) {
        val deltakerV1Record = deltakerKafkaPayloadBuilder.buildDeltakerV1Record(deltaker)
        if (unleashToggle.skalDelesMedEksterne(deltaker.deltakerliste.tiltakstype.tiltakskode)) {
            deltakerV1Producer.produce(deltakerV1Record)
        }
    }

    fun produceDeltakerV2Topic(deltaker: Deltaker, forcedUpdate: Boolean? = false) {
        val deltakerV2Record = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker, forcedUpdate)
        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode) ||
            unleashToggle.skalLeseArenaDataForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)
        ) {
            deltakerProducer.produce(deltakerV2Record)
        }
    }

    fun tombstone(deltakerId: UUID) {
        deltakerProducer.produceTombstone(deltakerId)
        deltakerV1Producer.produceTombstone(deltakerId)
    }
}
