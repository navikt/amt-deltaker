package no.nav.amt.deltaker.deltaker.endring

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.deltaker.toDeltakerEndringEndring
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerEndringHandlerTest {
    val deltakerHistorikkServiceMock = mockk<DeltakerHistorikkService>()

    @BeforeEach
    fun setup() {
        every { deltakerHistorikkServiceMock.getForDeltaker(any()) } returns emptyList()
    }

    @Test
    fun `endreDeltakersOppstart - fremtidig startdato - endrer også deltakelsesmengde`() {
        val nyStartdato = LocalDate.now().plusMonths(1)
        val gammelStartdato = nyStartdato.minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = nyStartdato,
            opprettet = nyStartdato.atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)

        endretDeltaker.startdato shouldBe nyStartdato
        endretDeltaker.dagerPerUke shouldBe fremtidigMengde.dagerPerUke
        endretDeltaker.deltakelsesprosent shouldBe fremtidigMengde.deltakelsesprosent
    }

    @Test
    fun `endreDeltakersOppstart - null startdato - endrer ikke deltakelsesmengde`() {
        val nyStartdato = null
        val gammelStartdato = LocalDate.now().minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = gammelStartdato.plusMonths(2),
            opprettet = gammelStartdato.plusMonths(2).atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)

        endretDeltaker.startdato shouldBe nyStartdato
        endretDeltaker.dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
        endretDeltaker.deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
    }

    @Test
    fun `endreDeltakersOppstart - eldre startdato - endrer ikke deltakelsesmengde`() {
        val gammelStartdato = LocalDate.now().minusMonths(1)
        val nyStartdato = gammelStartdato.minusMonths(1)

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = gammelStartdato,
            opprettet = gammelStartdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 42F,
            dagerPerUke = 2F,
            gyldigFra = gammelStartdato.plusMonths(2),
            opprettet = gammelStartdato.plusMonths(2).atStartOfDay(),
        )

        val gammelDeltakelsesmengder = Deltakelsesmengder(
            listOf(gjeldendeMengde, fremtidigMengde),
        )

        val deltaker = TestData.lagDeltaker(
            startdato = gammelStartdato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)

        endretDeltaker.startdato shouldBe nyStartdato
        endretDeltaker.dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
        endretDeltaker.deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fortid, venter pa oppstart - deltaker blir har sluttet`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )
        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerResult.startdato shouldBe endringsrequest.startdato
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - endret sluttdato i fortid, startdato mangler, venter pa oppstart - blir ikke aktuell`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = null,
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )
        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        deltakerResult.startdato shouldBe null
        deltakerResult.sluttdato shouldBe null
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fortid, deltar - deltaker blir har sluttet`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerResult.startdato shouldBe endringsrequest.startdato
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fremtid, deltar - deltaker blir venter pa oppstart`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().plusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        deltakerResult.startdato shouldBe endringsrequest.startdato
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fremtid, har sluttet - deltaker blir deltar`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerResult.startdato shouldBe endringsrequest.startdato
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - endret sluttdato`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            sluttdato = LocalDate.now().minusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `sjekkUtfall - endret sluttdato frem i tid - endrer status og sluttdato`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `sjekkUtfall - endret sluttarsak`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.SYK),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = SluttarsakRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `sjekkUtfall - avslutt deltakelse`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.AvsluttDeltakelse(LocalDate.now(), EndringAarsak.FattJobb, null, null),
        )
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerResult.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - avslutt deltakelse i fremtiden - deltaker får ny sluttdato, fremtidig status`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()
        resultat.erVellykket shouldBe true

        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerResult.status.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `sjekkUtfall - har sluttet, avslutt deltakelse i fremtiden - ny sluttdato, fremtidig status`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        resultat as DeltakerEndringUtfall.VellykketEndring
        val deltakerResult = resultat.deltaker
        val nesteStatus = resultat.nesteStatus

        deltakerResult.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerResult.status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato

        nesteStatus?.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        nesteStatus?.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        nesteStatus?.gyldigFra?.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        nesteStatus?.gyldigTil shouldBe null
    }

    @Test
    fun `sjekkUtfall - har sluttet, avslutt deltakelse i fortid - returnerer deltaker med ny sluttdato`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().minusDays(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        resultat as DeltakerEndringUtfall.VellykketEndring
        val deltakerResult = resultat.deltaker
        val nesteStatus = resultat.nesteStatus

        deltakerResult.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerResult.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        deltakerResult.status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
        deltakerResult.sluttdato shouldBe endringsrequest.sluttdato

        nesteStatus shouldBe null
    }

    @Test
    fun `sjekkUtfall - endre avslutning til fullfort`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.AVBRUTT),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(aarsak = EndringAarsak.FattJobb, harDeltatt = true, harFullfort = true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = true,
            forslagId = forslag.id,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.FULLFORT
        deltakerResult.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `sjekkUtfall - endre avslutning til avbrutt`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, false),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = false,
            forslagId = forslag.id,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.AVBRUTT
        deltakerResult.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `sjekkUtfall - endre avslutning ingen endring - gir erVellykket false`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = null,
            begrunnelse = "begrunnelse",
            harFullfort = true,
            forslagId = forslag.id,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe false
    }

    @Test
    fun `sjekkUtfall - reaktiver deltakelse lopende oppstart`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedLopendeOppstart(),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        deltakerResult.startdato shouldBe null
        deltakerResult.sluttdato shouldBe null
    }

    @Test
    fun `sjekkUtfall - reaktiver deltakelse felles oppstart`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart(),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val deltakerResult = resultat.getOrThrow()
        deltakerResult.status.type shouldBe DeltakerStatus.Type.SOKT_INN
        deltakerResult.startdato shouldBe null
        deltakerResult.sluttdato shouldBe null
    }
}
