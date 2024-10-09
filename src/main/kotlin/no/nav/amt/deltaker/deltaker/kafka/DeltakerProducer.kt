package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.lib.kafka.Producer
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerProducer(
    private val producer: Producer<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun produce(deltakerV2Dto: DeltakerV2Dto) {
        producer.produce(Environment.DELTAKER_V2_TOPIC, deltakerV2Dto.id.toString(), objectMapper.writeValueAsString(deltakerV2Dto))
    }

    fun produceTombstone(deltakerId: UUID) {
        producer.tombstone(Environment.DELTAKER_V2_TOPIC, deltakerId.toString())
    }
}
