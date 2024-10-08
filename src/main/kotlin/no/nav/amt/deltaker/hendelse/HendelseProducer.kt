package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.hendelse.model.Hendelse
import no.nav.amt.lib.kafka.Producer

class HendelseProducer(
    private val producer: Producer<String, String>,
) {
    fun produce(hendelse: Hendelse) {
        producer.produce(Environment.DELTAKER_HENDELSE_TOPIC, hendelse.deltaker.id.toString(), objectMapper.writeValueAsString(hendelse))
    }
}
