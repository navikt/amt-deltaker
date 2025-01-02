package no.nav.amt.deltaker

import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.Environment.Companion.HTTP_CLIENT_TIMEOUT_MS
import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.application.isReadyKey
import no.nav.amt.deltaker.application.plugins.applicationConfig
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureMonitoring
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.AmtArrangorClient
import no.nav.amt.deltaker.arrangor.ArrangorConsumer
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.AzureAdTokenClient
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
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
import no.nav.amt.deltaker.deltaker.kafka.DeltakerConsumer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerDtoMapperService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.isoppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.job.DeltakerStatusOppdateringService
import no.nav.amt.deltaker.job.StatusUpdateJob
import no.nav.amt.deltaker.job.leaderelection.LeaderElection
import no.nav.amt.deltaker.navansatt.NavAnsattConsumer
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerConsumer
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.utils.database.Database
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangHttpClient

fun main() {
    val server = embeddedServer(Netty, port = 8080, module = Application::module)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.application.attributes.put(isReadyKey, false)
            server.stop(gracePeriodMillis = 5_000, timeoutMillis = 30_000)
        },
    )
    server.start(wait = true)
}

fun Application.module() {
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

    val isOppfolgingstilfelleClient = IsOppfolgingstilfelleClient(
        baseUrl = environment.isOppfolgingstilfelleUrl,
        scope = environment.isOppfolgingstilfelleScope,
        azureAdTokenClient = azureAdTokenClient,
        httpClient = httpClient,
    )

    val kafkaProducer = Producer<String, String>(if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl())

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

    val poaoTilgangCachedClient = PoaoTilgangCachedClient.createDefaultCacheClient(
        PoaoTilgangHttpClient(
            baseUrl = environment.poaoTilgangUrl,
            tokenProvider = { runBlocking { azureAdTokenClient.getMachineToMachineTokenWithoutType(environment.poaoTilgangScope) } },
        ),
    )
    val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonServiceClient)
    val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonServiceClient)
    val navBrukerService = NavBrukerService(
        navBrukerRepository,
        amtPersonServiceClient,
        navEnhetService,
        navAnsattService,
    )

    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)

    val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        vedtakRepository,
        forslagRepository,
        endringFraArrangorRepository,
        importertFraArenaRepository,
    )

    val hendelseProducer = HendelseProducer(kafkaProducer)
    val hendelseService = HendelseService(hendelseProducer, navAnsattService, navEnhetService, arrangorService, deltakerHistorikkService)

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

    val deltakerDtoMapperService = DeltakerDtoMapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
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
    val vedtakService = VedtakService(vedtakRepository, hendelseService)
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
    )
    val pameldingService = PameldingService(
        deltakerService = deltakerService,
        deltakerlisteRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
        isOppfolgingstilfelleClient = isOppfolgingstilfelleClient,
    )

    val deltakerStatusOppdateringService = DeltakerStatusOppdateringService(deltakerRepository, deltakerService, unleashToggle)

    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        NavAnsattConsumer(navAnsattService),
        NavBrukerConsumer(navBrukerRepository, navEnhetService, deltakerService),
        TiltakstypeConsumer(tiltakstypeRepository),
        DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerStatusOppdateringService),
        DeltakerConsumer(
            deltakerRepository,
            deltakerlisteRepository,
            navBrukerService,
            deltakerEndringService,
            importertFraArenaRepository,
            unleashToggle,
        ),
        ArrangorMeldingConsumer(forslagService, deltakerService),
    )
    consumers.forEach { it.run() }

    configureAuthentication(environment)
    configureRouting(
        pameldingService,
        deltakerService,
        deltakerHistorikkService,
        tilgangskontrollService,
        deltakelserResponseMapper,
        deltakerProducerService,
        unleashToggle,
    )
    configureMonitoring()

    val statusUpdateJob = StatusUpdateJob(leaderElection, attributes, deltakerStatusOppdateringService)
    statusUpdateJob.startJob()

    val deltakelsesmengdeUpdateJob = DeltakelsesmengdeUpdateJob(leaderElection, attributes, deltakerEndringService, deltakerService)
    deltakelsesmengdeUpdateJob.startJob()

    attributes.put(isReadyKey, true)
}
