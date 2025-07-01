package no.nav.amt.deltaker.deltaker.forslag.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.arrangor.melding.Melding

class ArrangorMeldingProducer(
    private val producer: Producer<String, String>,
) {
    fun produce(melding: Melding) {
        producer.produce(Environment.ARRANGOR_MELDING_TOPIC, melding.id.toString(), objectMapper.writeValueAsString(melding))
    }
}
