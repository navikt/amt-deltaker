package no.nav.amt.deltaker.navenhet

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.amtperson.dto.NavEnhetDto
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import org.slf4j.LoggerFactory
import java.util.UUID

class NavEnhetConsumer(
    private val navEnhetService: NavEnhetService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.AMT_NAV_ENHET_TOPIC,
        consumeFunc = ::consume,
    )

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) throw IllegalArgumentException("Mottok uventet tombstone for nav-enhet med id $key")

        val dto = objectMapper.readValue<NavEnhetDto>(value)
        navEnhetService.upsert(dto.toModel())
        log.info("Lagret nav-enhet med id $key")
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}

fun NavEnhetDto.toModel() = NavEnhet(
    id = id,
    enhetsnummer = enhetId,
    navn = navn,
)
