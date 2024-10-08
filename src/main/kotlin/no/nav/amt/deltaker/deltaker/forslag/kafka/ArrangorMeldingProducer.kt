package no.nav.amt.deltaker.deltaker.forslag.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.arrangor.melding.Forslag
import org.slf4j.LoggerFactory

class ArrangorMeldingProducer(
    private val producer: Producer<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun produce(forslag: Forslag) {
        producer.produce(Environment.ARRANGOR_MELDING_TOPIC, forslag.id.toString(), objectMapper.writeValueAsString(forslag))
    }
}
