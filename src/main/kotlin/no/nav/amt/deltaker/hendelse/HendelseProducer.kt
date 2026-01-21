package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.outbox.OutboxService

class HendelseProducer(
    private val outboxService: OutboxService,
) {
    fun produce(hendelse: Hendelse) {
        outboxService.insertRecord(
            topic = Environment.DELTAKER_HENDELSE_TOPIC,
            key = hendelse.deltaker.id.toString(),
            value = hendelse,
        )
    }
}
