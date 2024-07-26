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
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerApiTest {
    private val deltakerService = mockk<DeltakerService>(relaxUnitFun = true)
    private val deltakerHistorikkService = mockk<DeltakerHistorikkService>()

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/innhold") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/startdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/sluttdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/sluttarsak") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/forleng") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/ikke-aktuell") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/avslutt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/reaktiver") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client
            .post("/deltaker/${UUID.randomUUID()}/vedtak/${UUID.randomUUID()}/fatt") {
                setBody("")
            }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post bakgrunnsinformasjon - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("bakgrunnsinformasjon")

        val deltaker = TestData.lagDeltaker(bakgrunnsinformasjon = endring.bakgrunnsinformasjon)
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") {
                postRequest(
                    BakgrunnsinformasjonRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        "bakgrunnsinformasjon",
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post innhold - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring =
            DeltakerEndring.Endring.EndreInnhold(listOf(Innhold("tekst", "kode", valgt = true, "beskrivelse")))

        val deltaker = TestData.lagDeltaker(innhold = endring.innhold)
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/innhold") {
                postRequest(
                    InnholdRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        endring.innhold,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post deltakelsesmengde - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(50F, 2F, "begrunnelse")

        val deltaker = TestData.lagDeltaker(
            deltakelsesprosent = endring.deltakelsesprosent,
            dagerPerUke = endring.dagerPerUke,
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") {
                postRequest(
                    DeltakelsesmengdeRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.deltakelsesprosent?.toInt(),
                        endring.dagerPerUke?.toInt(),
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post startdato - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.EndreStartdato(LocalDate.now().minusDays(2), LocalDate.now().plusMonths(2), "begrunnelse")

        val deltaker = TestData.lagDeltaker(startdato = endring.startdato)
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/startdato") {
                postRequest(
                    StartdatoRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.startdato,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post sluttdato - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.EndreSluttdato(LocalDate.now().minusDays(2), "begrunnelse")

        val deltaker = TestData.lagDeltaker(sluttdato = endring.sluttdato)
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/sluttdato") {
                postRequest(
                    SluttdatoRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post sluttarsak - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring =
            DeltakerEndring.Endring.EndreSluttarsak(DeltakerEndring.Aarsak(type = DeltakerEndring.Aarsak.Type.FATT_JOBB, null), null)

        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name),
            ),
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/sluttarsak") {
                postRequest(
                    SluttarsakRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post forleng - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.ForlengDeltakelse(LocalDate.now().plusWeeks(2), "begrunnelse")

        val deltaker = TestData.lagDeltaker(sluttdato = endring.sluttdato)
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/forleng") {
                postRequest(
                    ForlengDeltakelseRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post ikke aktuell - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.IkkeAktuell(
            DeltakerEndring.Aarsak(type = DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            "begrunnelse",
        )

        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsak = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name),
            ),
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/ikke-aktuell") {
                postRequest(
                    IkkeAktuellRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post avslutt deltakelse - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.AvsluttDeltakelse(
            DeltakerEndring.Aarsak(type = DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            LocalDate.now(),
            "begrunnelse",
        )

        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name),
            ),
            sluttdato = endring.sluttdato,
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/avslutt") {
                postRequest(
                    AvsluttDeltakelseRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `post reaktiver - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.ReaktiverDeltakelse(LocalDate.now(), "begrunnelse")

        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.VENTER_PA_OPPSTART,
            ),
            startdato = null,
            sluttdato = null,
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/reaktiver") {
                postRequest(
                    ReaktiverDeltakelseRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        endring.begrunnelse,
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
            }
    }

    @Test
    fun `fatt vedtak - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)

        val historikk = listOf(DeltakerHistorikk.Vedtak(vedtak))

        coEvery { deltakerService.fattVedtak(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${deltaker.id}/vedtak/${vedtak.id}/fatt") {
                postRequest("")
            }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
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
                mockk(),
                mockk(),
            )
        }
    }
}
