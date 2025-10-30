package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerProducer(
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerV2Dto: DeltakerKafkaPayload) {
        producer.produce(Environment.DELTAKER_V2_TOPIC, deltakerV2Dto.id.toString(), objectMapper.writeValueAsString(deltakerV2Dto))
    }

    fun produceTombstone(deltakerId: UUID) {
        producer.tombstone(Environment.DELTAKER_V2_TOPIC, deltakerId.toString())
    }
}
