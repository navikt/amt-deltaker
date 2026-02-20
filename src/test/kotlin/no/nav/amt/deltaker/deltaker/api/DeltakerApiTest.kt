package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import no.nav.amt.deltaker.deltaker.api.DtoMappers.deltakerEndringResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.randomEnhetsnummer
import no.nav.amt.deltaker.utils.data.TestData.randomIdent
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerApiTest : RouteTestBase() {
    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        withTestApplicationContext { client ->
            client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post bakgrunnsinformasjon til felles endepunkt for endringer - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("bakgrunnsinformasjon")
        val request = BakgrunnsinformasjonRequest(
            endretAv = randomIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            bakgrunnsinformasjon = endring.bakgrunnsinformasjon,
        )

        val deltaker = lagDeltaker(bakgrunnsinformasjon = endring.bakgrunnsinformasjon)
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(deltakerEndringResponseFromDeltaker(deltaker, historikk))

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltaker.id}/deltaker-endring") {
                postRequest(request)
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }

        coVerify { deltakerService.upsertEndretDeltaker(deltaker.id, request) }
    }

    @Test
    fun `post innhold - har tilgang - returnerer 200`() {
        val endring =
            DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "kode", valgt = true, "beskrivelse")))

        val deltaker = lagDeltaker(innhold = Deltakelsesinnhold("test", endring.innhold))
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(deltakerEndringResponseFromDeltaker(deltaker, historikk))

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    InnholdRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        Deltakelsesinnhold(endring.ledetekst, endring.innhold),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post deltakelsesmengde - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(50F, 2F, LocalDate.now(), "begrunnelse")

        val deltaker = lagDeltaker(
            deltakelsesprosent = endring.deltakelsesprosent,
            dagerPerUke = endring.dagerPerUke,
        )
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(deltakerEndringResponseFromDeltaker(deltaker, historikk))

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    DeltakelsesmengdeRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.deltakelsesprosent?.toInt(),
                        endring.dagerPerUke?.toInt(),
                        endring.begrunnelse,
                        LocalDate.now(),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post startdato - har tilgang - returnerer 200`() {
        val endring =
            DeltakerEndring.Endring.EndreStartdato(LocalDate.now().minusDays(2), LocalDate.now().plusMonths(2), "begrunnelse")

        val deltaker = lagDeltaker(startdato = endring.startdato)
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(deltakerEndringResponseFromDeltaker(deltaker, historikk))

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    StartdatoRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.startdato,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post sluttdato - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.EndreSluttdato(LocalDate.now().minusDays(2), "begrunnelse")
        val deltaker = lagDeltaker(sluttdato = endring.sluttdato)
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(
                deltaker,
                historikk,
            ),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    SluttdatoRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post sluttarsak - har tilgang - returnerer 200`() {
        val endring =
            DeltakerEndring.Endring.EndreSluttarsak(
                DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.FATT_JOBB,
                    null,
                ),
                null,
            )

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name),
            ),
        )
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(
                deltaker,
                historikk,
            ),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    SluttarsakRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post forleng - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.ForlengDeltakelse(LocalDate.now().plusWeeks(2), "begrunnelse")

        val deltaker = lagDeltaker(sluttdato = endring.sluttdato)
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(
                deltaker,
                historikk,
            ),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    ForlengDeltakelseRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post ikke aktuell - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.IkkeAktuell(
            DeltakerEndring.Aarsak(type = DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            "begrunnelse",
        )

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsakType = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name),
            ),
        )
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(
                deltaker,
                historikk,
            ),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    IkkeAktuellRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post avslutt deltakelse - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.AvsluttDeltakelse(
            DeltakerEndring.Aarsak(type = DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            LocalDate.now(),
            "begrunnelse",
        )

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak!!.type.name),
            ),
            sluttdato = endring.sluttdato,
        )
        val historikk = listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(
                deltaker,
                historikk,
            ),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    AvsluttDeltakelseRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.sluttdato,
                        endring.aarsak,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post endre avslutning - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.EndreAvslutning(
            null,
            true,
            LocalDate.now(),
            "begrunnelse",
        )

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.FULLFORT,
                aarsakType = null,
            ),
        )
        val historikk =
            listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(deltaker, historikk),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    EndreAvslutningRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.aarsak,
                        endring.begrunnelse,
                        endring.sluttdato,
                        endring.harFullfort,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post reaktiver - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.ReaktiverDeltakelse(LocalDate.now(), "begrunnelse")

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )
        val historikk =
            listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(deltaker, historikk),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    ReaktiverDeltakelseRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post fjern oppstartsdato - har tilgang - returnerer 200`() {
        val endring = DeltakerEndring.Endring.FjernOppstartsdato("begrunnelse")

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )
        val historikk =
            listOf(DeltakerHistorikk.Endring(lagDeltakerEndring(endring = endring)))

        coEvery { deltakerService.upsertEndretDeltaker(any(), any()) } returns deltaker
        every { deltakerHistorikkService.getForDeltaker(any()) } returns historikk

        val expectedBody = objectMapper.writeValueAsString(
            deltakerEndringResponseFromDeltaker(deltaker, historikk),
        )

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${UUID.randomUUID()}/deltaker-endring") {
                postRequest(
                    FjernOppstartsdatoRequest(
                        randomIdent(),
                        randomEnhetsnummer(),
                        null,
                        endring.begrunnelse,
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe expectedBody
        }
    }

    @Test
    fun `post sist-besokt - har tilgang - returnerer 200`() {
        val deltakerInTest = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        val sistBesoktInTest = ZonedDateTime.now()

        every { deltakerService.oppdaterSistBesokt(deltakerInTest.id, sistBesoktInTest) } just Runs

        withTestApplicationContext { client ->
            val response = client.post("/deltaker/${deltakerInTest.id}/sist-besokt") {
                postRequest(sistBesoktInTest)
            }

            response.status shouldBe HttpStatusCode.OK
        }

        verify(exactly = 1) {
            deltakerService.oppdaterSistBesokt(
                deltakerId = deltakerInTest.id,
                sistBesokt = sistBesoktInTest.withZoneSameInstant(ZoneOffset.UTC),
            )
        }
    }
}
