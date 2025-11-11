package no.nav.amt.deltaker.utils

import io.getunleash.FakeUnleash
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRequestValidation
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.external.data.DeltakelserResponseMapper
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.utils.applicationConfig
import no.nav.amt.lib.utils.database.Database
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import org.junit.jupiter.api.BeforeEach
import kotlin.test.AfterTest

abstract class RouteTestBase {
    protected open val deltakelserResponseMapper: DeltakelserResponseMapper = mockk(relaxed = true)

    protected val pameldingService: PameldingService = mockk(relaxed = true)
    protected val deltakerService: DeltakerService = mockk(relaxed = true)
    protected val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    protected val deltakerHistorikkService: DeltakerHistorikkService = mockk(relaxed = true)
    protected val deltakerProducerService: DeltakerProducerService = mockk(relaxed = true)
    protected val vedtakService: VedtakService = mockk(relaxed = true)
    protected val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService = mockk(relaxed = true)
    protected val vurderingService: VurderingService = mockk(relaxed = true)
    protected val hendelseService: HendelseService = mockk(relaxed = true)
    protected val endringFraTiltakskoordinatorService: EndringFraTiltakskoordinatorService = mockk(relaxed = true)
    protected val arrangorService = mockk<ArrangorService>()
    protected val navEnhetService = mockk<NavEnhetService>()

    protected val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    protected val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    protected val unleashClient = FakeUnleash()
    protected open val unleashToggle = UnleashToggle(unleashClient)

    protected val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()

    @BeforeEach
    protected fun init() {
        clearAllMocks()
        configureEnvForAuthentication()

        mockkObject(Database)

        coEvery { Database.transaction(any<suspend () -> Any>()) } coAnswers {
            val block = firstArg<suspend () -> Any>()
            block()
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    protected fun <T : Any> withTestApplicationContext(block: suspend (HttpClient) -> T): T {
        lateinit var result: T

        testApplication {
            application {
                configureSerialization()
                configureAuthentication(Environment())
                configureRequestValidation(
                    opprettKladdRequestValidator = opprettKladdRequestValidator,
                )
                configureRouting(
                    pameldingService,
                    deltakerService,
                    deltakerRepository,
                    deltakerHistorikkService,
                    tilgangskontrollService,
                    deltakelserResponseMapper,
                    deltakerProducerService,
                    vedtakService,
                    unleashToggle,
                    innsokPaaFellesOppstartService,
                    vurderingService,
                    hendelseService,
                    endringFraTiltakskoordinatorService,
                    navEnhetService,
                )
                setUpTestRoute()
            }

            result =
                block(
                    createClient {
                        install(ContentNegotiation) {
                            jackson { applicationConfig() }
                        }
                    },
                )
        }

        return result
    }

    private fun Application.setUpTestRoute() {
        routing {
            authenticate("SYSTEM") {
                get("/deltaker") {
                    call.respondText("System har tilgang!")
                }
            }
        }
    }
}
