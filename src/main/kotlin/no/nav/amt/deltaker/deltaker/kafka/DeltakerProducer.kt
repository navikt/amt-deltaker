package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerProducer(
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerV2Dto: DeltakerKafkaPayload) = producer.produce(
        topic = Environment.DELTAKER_V2_TOPIC,
        key = deltakerV2Dto.id.toString(),
        value = objectMapper.writeValueAsString(deltakerV2Dto),
    )

    fun produceTombstone(deltakerId: UUID) = producer.tombstone(
        topic = Environment.DELTAKER_V2_TOPIC,
        key = deltakerId.toString(),
    )
}
