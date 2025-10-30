package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV1Dto
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerV1Producer(
    private val producer: Producer<String, String>,
) {
    fun produce(deltakerV1Dto: DeltakerV1Dto) {
        producer.produce(Environment.DELTAKER_V1_TOPIC, deltakerV1Dto.id.toString(), objectMapper.writeValueAsString(deltakerV1Dto))
    }

    fun produceTombstone(deltakerId: UUID) {
        producer.tombstone(Environment.DELTAKER_V1_TOPIC, deltakerId.toString())
    }
}
