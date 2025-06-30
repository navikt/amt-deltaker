package no.nav.amt.deltaker.navbruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.amtperson.dto.NavBrukerDto
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.slf4j.LoggerFactory
import java.util.UUID

class NavBrukerConsumer(
    private val repository: NavBrukerRepository,
    private val navEnhetService: NavEnhetService,
    private val deltakerService: DeltakerService,
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = ManagedKafkaConsumer(
        topic = Environment.AMT_NAV_BRUKER_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = Environment.KAFKA_CONSUMER_GROUP_ID,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for nav-bruker: $key, skal ikke skje.")
            return
        }
        val lagretNavBruker = repository.get(key).getOrNull()
        val navBrukerDto = objectMapper.readValue<NavBrukerDto>(value)
        if (harEndredePersonopplysninger(lagretNavBruker, navBrukerDto)) {
            navBrukerDto.navEnhet?.let { navEnhetService.hentEllerOpprettNavEnhet(it.enhetId) }
            repository.upsert(navBrukerDto.tilNavBruker())
            val harEndretPersonident = lagretNavBruker?.personident != navBrukerDto.personident

            deltakerService.produserDeltakereForPerson(
                navBrukerDto.personident,
                publiserTilDeltakerV1 = harEndretPersonident,
            )
        }
    }

    override fun start() = consumer.start()

    private fun harEndredePersonopplysninger(navBruker: NavBruker?, navBrukerDto: NavBrukerDto): Boolean = if (navBruker == null) {
        true
    } else {
        navBrukerDto.tilNavBruker() != navBruker
    }
}
