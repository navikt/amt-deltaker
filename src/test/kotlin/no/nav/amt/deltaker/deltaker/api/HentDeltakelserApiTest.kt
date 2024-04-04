package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponse
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
import no.nav.amt.deltaker.deltaker.api.model.DeltakerKort
import no.nav.amt.deltaker.deltaker.api.model.Periode
import no.nav.amt.deltaker.deltaker.api.utils.postVeilederRequest
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class HentDeltakelserApiTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)
    private val deltakerService = mockk<DeltakerService>(relaxUnitFun = true)
    private val deltakerHistorikkService = mockk<DeltakerHistorikkService>()
    private val arrangorService = mockk<ArrangorService>()
    private val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/deltakelser") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `skal teste tilgangskontroll - har ikke tilgang - returnerer 403`() = testApplication {
        coEvery { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )

        setUpTestApplication()
        client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `post deltakelser - har tilgang, deltaker deltar - returnerer 200`() = testApplication {
        coEvery { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val innsoktDato = LocalDate.now().minusDays(4)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR AS"),
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val historikk = listOf(
            DeltakerHistorikk.Vedtak(
                TestData.lagVedtak(
                    opprettet = innsoktDato.atStartOfDay(),
                ),
            ),
        )

        coEvery { deltakerService.getDeltakelser(any()) } returns listOf(deltaker)
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk
        coEvery { deltakerHistorikkService.getInnsoktDato(any()) } returns innsoktDato

        val forventetRespons = DeltakelserResponse(
            aktive = listOf(
                DeltakerKort(
                    deltakerId = deltaker.id,
                    tittel = "Oppfølging hos Arrangør AS",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = deltaker.deltakerliste.tiltakstype.navn,
                        tiltakskode = deltaker.deltakerliste.tiltakstype.type,
                    ),
                    status = DeltakerKort.Status(
                        status = DeltakerStatus.Type.DELTAR,
                        statustekst = "Deltar",
                        aarsak = null,
                    ),
                    innsoktDato = innsoktDato,
                    sistEndretdato = null,
                    periode = Periode(
                        startdato = deltaker.startdato,
                        sluttdato = deltaker.sluttdato,
                    ),
                ),
            ),
            historikk = emptyList(),
        )

        setUpTestApplication()
        client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
        }
    }

    @Test
    fun `post deltakelser - har tilgang, kladd og avsluttet deltakelse - returnerer 200`() = testApplication {
        coEvery { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        val innsoktDato = LocalDate.now().minusDays(4)
        val deltakerKladd = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR AS"),
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )
        val avsluttetDeltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(overordnetArrangorId = null, navn = "ARRANGØR OG SØNN AS"),
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            startdato = LocalDate.now().minusMonths(3),
            sluttdato = LocalDate.now().minusDays(2),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB),
        )
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Vedtak(
                TestData.lagVedtak(
                    opprettet = innsoktDato.atStartOfDay(),
                ),
            ),
        )

        coEvery { deltakerService.getDeltakelser(any()) } returns listOf(deltakerKladd, avsluttetDeltaker)
        coEvery { deltakerHistorikkService.getForDeltaker(deltakerKladd.id) } returns emptyList()
        coEvery { deltakerHistorikkService.getInnsoktDato(emptyList()) } returns null
        coEvery { deltakerHistorikkService.getForDeltaker(avsluttetDeltaker.id) } returns deltakerhistorikk
        coEvery { deltakerHistorikkService.getInnsoktDato(match { it.isNotEmpty() }) } returns innsoktDato

        val forventetRespons = DeltakelserResponse(
            aktive = listOf(
                DeltakerKort(
                    deltakerId = deltakerKladd.id,
                    tittel = "Oppfølging hos Arrangør AS",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = deltakerKladd.deltakerliste.tiltakstype.navn,
                        tiltakskode = deltakerKladd.deltakerliste.tiltakstype.type,
                    ),
                    status = DeltakerKort.Status(
                        status = DeltakerStatus.Type.KLADD,
                        statustekst = "Kladden er ikke delt",
                        aarsak = null,
                    ),
                    innsoktDato = null,
                    sistEndretdato = deltakerKladd.sistEndret.toLocalDate(),
                    periode = null,
                ),
            ),
            historikk = listOf(
                DeltakerKort(
                    deltakerId = avsluttetDeltaker.id,
                    tittel = "Oppfølging hos Arrangør og Sønn AS",
                    tiltakstype = DeltakelserResponse.Tiltakstype(
                        navn = avsluttetDeltaker.deltakerliste.tiltakstype.navn,
                        tiltakskode = avsluttetDeltaker.deltakerliste.tiltakstype.type,
                    ),
                    status = DeltakerKort.Status(
                        status = DeltakerStatus.Type.HAR_SLUTTET,
                        statustekst = "Har sluttet",
                        aarsak = "fått jobb",
                    ),
                    innsoktDato = innsoktDato,
                    sistEndretdato = null,
                    periode = Periode(
                        startdato = avsluttetDeltaker.startdato,
                        sluttdato = avsluttetDeltaker.sluttdato,
                    ),
                ),
            ),
        )

        setUpTestApplication()
        client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
        }
    }

    @Test
    fun `post deltakelser - har tilgang, ingen deltakelser - returnerer 200`() = testApplication {
        coEvery { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        coEvery { deltakerService.getDeltakelser(any()) } returns emptyList()

        val forventetRespons = DeltakelserResponse(
            aktive = emptyList(),
            historikk = emptyList(),
        )

        setUpTestApplication()
        client.post("/deltakelser") { postVeilederRequest(deltakelserRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                mockk(),
                deltakerService,
                deltakerHistorikkService,
                tilgangskontrollService,
                deltakelserResponseMapper,
            )
        }
    }

    private val deltakelserRequest = DeltakelserRequest(TestData.randomIdent())
}
