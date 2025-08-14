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
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.request.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.request.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.request.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.request.EndreAvslutningRequest
import no.nav.amt.deltaker.deltaker.api.model.request.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.request.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.request.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.request.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.request.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.request.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.request.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.request.StartdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerApiTest {
    private val deltakerService = mockk<DeltakerService>(relaxUnitFun = true)
    private val deltakerHistorikkService = mockk<DeltakerHistorikkService>()

    @BeforeEach
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/innhold") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/endre-avslutning") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/startdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/sluttdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/sluttarsak") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/forleng") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/ikke-aktuell") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/avslutt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/reaktiver") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/fjern-oppstartsdato") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
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
            DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "kode", valgt = true, "beskrivelse")))

        val deltaker = TestData.lagDeltaker(innhold = Deltakelsesinnhold("test", endring.innhold))
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/innhold") {
                postRequest(
                    InnholdRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        Deltakelsesinnhold(endring.ledetekst, endring.innhold),
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

        val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(50F, 2F, LocalDate.now(), "begrunnelse")

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
                        LocalDate.now(),
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
                aarsak = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak!!.type.name),
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
    fun `post endre avslutning - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.EndreAvslutning(
            null,
            true,
            "begrunnelse",
        )

        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.FULLFORT,
                aarsak = null,
            ),
        )
        val historikk = listOf(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        coEvery { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        client
            .post("/deltaker/${UUID.randomUUID()}/endre-avslutning") {
                postRequest(
                    EndreAvslutningRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                        endring.harFullfort,
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
    fun `post fjern oppstartsdato - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        val endring = DeltakerEndring.Endring.FjernOppstartsdato("begrunnelse")

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
            .post("/deltaker/${UUID.randomUUID()}/fjern-oppstartsdato") {
                postRequest(
                    FjernOppstartsdatoRequest(
                        TestData.randomIdent(),
                        TestData.randomEnhetsnummer(),
                        null,
                        endring.begrunnelse,
                    ),
                )
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
                opprettKladdRequestValidator = mockk(),
                pameldingService = mockk(),
                deltakerService = deltakerService,
                deltakerHistorikkService = deltakerHistorikkService,
                tilgangskontrollService = mockk(),
                deltakelserResponseMapper = mockk(),
                deltakerProducerService = mockk(),
                vedtakService = mockk(),
                unleashToggle = mockk(),
                innsokPaaFellesOppstartService = mockk(),
                vurderingService = mockk(),
                hendelseService = mockk(),
                endringFraTiltakskoordinatorService = mockk(),
            )
        }
    }
}
