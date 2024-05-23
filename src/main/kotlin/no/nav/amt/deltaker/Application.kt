package no.nav.amt.deltaker

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
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.deltaker.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV2MapperService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
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

    Database.init(environment)

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

    val arrangorRepository = ArrangorRepository()
    val navAnsattRepository = NavAnsattRepository()
    val navEnhetRepository = NavEnhetRepository()
    val navBrukerRepository = NavBrukerRepository()
    val tiltakstypeRepository = TiltakstypeRepository()
    val deltakerlisteRepository = DeltakerlisteRepository()
    val deltakerRepository = DeltakerRepository()
    val deltakerEndringRepository = DeltakerEndringRepository()
    val vedtakRepository = VedtakRepository()

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

    val deltakerHistorikkService = DeltakerHistorikkService(deltakerEndringRepository, vedtakRepository)

    val hendelseProducer = HendelseProducer()
    val hendelseService = HendelseService(hendelseProducer, navAnsattService, navEnhetService, arrangorService, deltakerHistorikkService)

    val deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
    val deltakerEndringService = DeltakerEndringService(deltakerEndringRepository, navAnsattService, navEnhetService, hendelseService)
    val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    val deltakerProducer = DeltakerProducer(deltakerV2MapperService = deltakerV2MapperService)

    val vedtakService = VedtakService(vedtakRepository, hendelseService)
    val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerProducer = deltakerProducer,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
    )
    val pameldingService = PameldingService(
        deltakerService = deltakerService,
        deltakerlisteRepository = deltakerlisteRepository,
        navBrukerService = navBrukerService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        vedtakService = vedtakService,
    )

    val deltakerStatusOppdateringService = DeltakerStatusOppdateringService(deltakerRepository, deltakerService)

    val consumers = listOf(
        ArrangorConsumer(arrangorRepository),
        NavAnsattConsumer(navAnsattService),
        NavBrukerConsumer(navBrukerRepository, navEnhetService, deltakerService),
        TiltakstypeConsumer(tiltakstypeRepository),
        DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService),
    )
    consumers.forEach { it.run() }

    configureAuthentication(environment)
    configureRouting(
        pameldingService,
        deltakerService,
        deltakerHistorikkService,
        tilgangskontrollService,
        deltakelserResponseMapper,
    )
    configureMonitoring()

    val statusUpdateJob = StatusUpdateJob(leaderElection, attributes, deltakerStatusOppdateringService)
    statusUpdateJob.startJob()

    attributes.put(isReadyKey, true)
}
