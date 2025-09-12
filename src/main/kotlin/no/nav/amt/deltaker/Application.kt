package no.nav.amt.deltaker

import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.Environment.Companion.HTTP_CLIENT_TIMEOUT_MS
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureMonitoring
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.ArrangorConsumer
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.deltaker.DeltakelserResponseMapper
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakelsesmengdeUpdateJob
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerConsumer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerDtoMapperService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.StatusUpdateJob
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerConsumer
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetConsumer
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.ShutdownHandlers
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.ktor.routing.isReadyKey
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.database.Database
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangHttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

fun main() {
    val log = LoggerFactory.getLogger("shutdownlogger")
    lateinit var shutdownHandlers: ShutdownHandlers

    val server = embeddedServer(Netty, port = 8080) {
        shutdownHandlers = module()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Received shutdown signal")
            server.application.attributes.put(isReadyKey, false)

            runBlocking {
                log.info("Shutting down Kafka consumers")
                shutdownHandlers.shutdownConsumers()

                log.info("Shutting down server")
                server.stop(
                    shutdownGracePeriod = 5,
                    shutdownTimeout = 20,
                    timeUnit = TimeUnit.SECONDS,
                )
                log.info("Shut down server completed")

                log.info("Shutting down database")
                Database.close()

                log.info("Shutting down producers")
                shutdownHandlers.shutdownProducers()
            }
        },
    )
    server.start(wait = true)
}

fun Application.module(): ShutdownHandlers {
    configureSerialization()

    val environment = Environment()

    Database.init(environment.databaseConfig)

    val httpClient = HttpClient(Apache) {
        engine {
            socketTimeout = HTTP_CLIENT_TIMEOUT_MS
            connectTimeout = HTTP_CLIENT_TIMEOUT_MS
            connectionRequestTimeout = HTTP_CLIENT_TIMEOUT_MS * 2
        }
        install(ContentNegotiation) {
            jackson { applicationConfig() }
        }
    }

    val leaderElection = LeaderElection(httpClient, environment.electorPath)

    val azureAdTokenClient = AzureAdTokenClient(
        azureAdTokenUrl = environment.azureAdTokenUrl,
        clientId = environment.azureClientId,
        clientSecret = environment.azureClientSecret,
        httpClient = httpClient,
    )

    val amtPersonServiceClient = AmtPersonServiceClient(
        baseUrl = environment.amtPersonServiceUrl,
        scope = environment.amtPersonServiceScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val amtArrangorClient = AmtArrangorClient(
        baseUrl = environment.amtArrangorUrl,
        scope = environment.amtArrangorScope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    )

    val isOppfolgingsTilfelleClient = IsOppfolgingstilfelleClient(
        baseUrl = environment.isOppfolgingstilfelleUrl,
        scope = environment.isOppfolgingstilfelleScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
    )

    val kafkaProducer = Producer<String, String>(
        kafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
        addShutdownHook = false,
    )

    val arrangorRepository = ArrangorRepository()
    val navAnsattRepository = NavAnsattRepository()
    val navEnhetRepository = NavEnhetRepository()
    val navBrukerRepository = NavBrukerRepository()
    val tiltakstypeRepository = TiltakstypeRepository()
    val deltakerlisteRepository = DeltakerlisteRepository()
    val deltakerRepository = DeltakerRepository()
    val deltakerEndringRepository = DeltakerEndringRepository()
    val vedtakRepository = VedtakRepository()
    val forslagRepository = ForslagRepository()
    val endringFraArrangorRepository = EndringFraArrangorRepository()
    val importertFraArenaRepository = ImportertFraArenaRepository()
    val vurderingRepository = VurderingRepository()

    val poaoTilgangCachedClient = PoaoTilgangCachedClient.createDefaultCacheClient(
        PoaoTilgangHttpClient(
            baseUrl = environment.poaoTilgangUrl,
            tokenProvider = { runBlocking { azureAdTokenClient.getMachineToMachineTokenWithoutType(environment.poaoTilgangScope) } },
        ),
    )
    val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)
    val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient, navEnhetService)
    val navBrukerService = NavBrukerService(
        navBrukerRepository,
        amtPersonServiceClient,
        navEnhetService,
        navAnsattService,
    )
    val vurderingService = VurderingService(vurderingRepository)
    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)
    val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
    val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
    val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()
    val endringFraTiltakskoordinatorService =
        EndringFraTiltakskoordinatorService(endringFraTiltakskoordinatorRepository, navAnsattService)

    val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        vedtakRepository,
        forslagRepository,
        endringFraArrangorRepository,
        importertFraArenaRepository,
        innsokPaaFellesOppstartRepository,
        endringFraTiltakskoordinatorRepository,
        vurderingService,
    )

    val hendelseProducer = HendelseProducer(kafkaProducer)
    val hendelseService = HendelseService(
        hendelseProducer = hendelseProducer,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        arrangorService = arrangorService,
        deltakerHistorikkService = deltakerHistorikkService,
        vurderingService = vurderingService,
    )

    val unleash = DefaultUnleash(
        UnleashConfig
            .builder()
            .appName(environment.appName)
            .instanceId(environment.appName)
            .unleashAPI("${environment.unleashUrl}/api")
            .apiKey(environment.unleashApiToken)
            .build(),
    )
    val unleashToggle = UnleashToggle(unleash)

    val deltakerDtoMapperService =
        DeltakerDtoMapperService(navAnsattService, navEnhetService, deltakerHistorikkService, vurderingRepository)
    val deltakerProducer = DeltakerProducer(kafkaProducer)
    val deltakerV1Producer = DeltakerV1Producer(kafkaProducer)
    val deltakerProducerService = DeltakerProducerService(deltakerDtoMapperService, deltakerProducer, deltakerV1Producer, unleashToggle)

    val forslagService =
        ForslagService(forslagRepository, ArrangorMeldingProducer(kafkaProducer), deltakerRepository, deltakerProducerService)

    val deltakerEndringService =
        DeltakerEndringService(
            deltakerEndringRepository,
            navAnsattService,
            navEnhetService,
            hendelseService,
            forslagService,
            deltakerHistorikkService,
        )
    val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    val endringFraArrangorService = EndringFraArrangorService(endringFraArrangorRepository, hendelseService, deltakerHistorikkService)
    val vedtakService = VedtakService(vedtakRepository)
    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerProducerService = deltakerProducerService,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        endringFraArrangorService = endringFraArrangorService,
        forslagService = forslagService,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        unleashToggle = unleashToggle,
        endringFraTiltakskoordinatorService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navAnsattService,
        navEnhetService,
    )

    val opprettKladdRequestValidator = OpprettKladdRequestValidator(
        deltakerlisteRepository = deltakerlisteRepository,
        brukerService = navBrukerService,
        personServiceClient = amtPersonServiceClient,
        isOppfolgingsTilfelleClient = isOppfolgingsTilfelleClient,
    )

    val pameldingService = PameldingService(
        deltakerService = deltakerService,
        deltakerListeRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
    )

    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        NavAnsattConsumer(navAnsattService),
        NavBrukerConsumer(navBrukerRepository, navEnhetService, deltakerService),
        TiltakstypeConsumer(tiltakstypeRepository),
        DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService),
        DeltakerConsumer(
            deltakerRepository,
            deltakerlisteRepository,
            navBrukerService,
            deltakerEndringService,
            importertFraArenaRepository,
            vurderingRepository,
            unleashToggle,
        ),
        ArrangorMeldingConsumer(forslagService, deltakerService, vurderingService, deltakerProducerService, unleashToggle),
        NavEnhetConsumer(navEnhetService),
    )
    consumers.forEach { it.start() }

    configureAuthentication(environment)

    configureRequestValidation(
        opprettKladdRequestValidator = opprettKladdRequestValidator,
    )

    configureRouting(
        pameldingService = pameldingService,
        deltakerService = deltakerService,
        deltakerHistorikkService = deltakerHistorikkService,
        tilgangskontrollService = tilgangskontrollService,
        deltakelserResponseMapper = deltakelserResponseMapper,
        deltakerProducerService = deltakerProducerService,
        vedtakService = vedtakService,
        unleashToggle = unleashToggle,
        innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
        vurderingService = vurderingService,
        hendelseService = hendelseService,
        endringFraTiltakskoordinatorService = endringFraTiltakskoordinatorService,
    )
    configureMonitoring()

    val statusUpdateJob = StatusUpdateJob(leaderElection, attributes, deltakerService)
    statusUpdateJob.startJob()

    val deltakelsesmengdeUpdateJob = DeltakelsesmengdeUpdateJob(leaderElection, attributes, deltakerEndringService, deltakerService)
    deltakelsesmengdeUpdateJob.startJob()

    attributes.put(isReadyKey, true)

    fun shutdownKafkaProducers() {
        try {
            kafkaProducer.close()
        } catch (e: Exception) {
            log.error("Error shutting down producers", e)
        }
    }

    suspend fun shutdownKafkaConsumers() {
        consumers.forEach {
            try {
                it.close()
            } catch (e: Exception) {
                log.error("Error shutting down consumer", e)
            }
        }
    }

    return ShutdownHandlers(shutdownProducers = { shutdownKafkaProducers() }, shutdownConsumers = { shutdownKafkaConsumers() })
}
