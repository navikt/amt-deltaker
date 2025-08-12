package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.utils.objectMapper

class HendelseProducer(
    private val producer: Producer<String, String>,
) {
    fun produce(hendelse: Hendelse) {
        producer.produce(Environment.DELTAKER_HENDELSE_TOPIC, hendelse.deltaker.id.toString(), objectMapper.writeValueAsString(hendelse))
    }
}
