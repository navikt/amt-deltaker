package no.nav.amt.deltaker.deltaker.endring

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.deltaker.toDeltakerEndringEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedDirekteVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedTrengerGodkjenning
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
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
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
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

        val deltaker = lagDeltaker(
            startdato = gammelStartdato,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)
        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe fremtidigMengde.dagerPerUke
            deltakelsesprosent shouldBe fremtidigMengde.deltakelsesprosent
        }
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

        val deltaker = lagDeltaker(
            startdato = gammelStartdato,
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)
        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
            deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
        }
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

        val deltaker = lagDeltaker(
            startdato = gammelStartdato,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
        )

        val endretDeltaker = endreDeltakersOppstart(deltaker, nyStartdato, null, gammelDeltakelsesmengder)
        assertSoftly(endretDeltaker) {
            startdato shouldBe nyStartdato
            dagerPerUke shouldBe gjeldendeMengde.dagerPerUke
            deltakelsesprosent shouldBe gjeldendeMengde.deltakelsesprosent
        }
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fortid, venter pa oppstart - deltaker blir har sluttet`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - endret sluttdato i fortid, startdato mangler, venter pa oppstart - blir ikke aktuell`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            startdato shouldBe null
            sluttdato shouldBe null
        }
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fortid, deltar - deltaker blir har sluttet`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fremtid, fullfort - deltaker blir venter pa oppstart`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fremtid, deltar - deltaker blir venter pa oppstart`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - endret start- og sluttdato i fremtid, har sluttet - deltaker blir deltar`() = runTest {
        val deltaker = lagDeltaker(
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.DELTAR
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - endret sluttdato`() = runTest {
        val deltaker = lagDeltaker()
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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
    fun `sjekkUtfall - endret sluttdato frem i tid - endrer status og sluttdato`() = runTest {
        val deltaker = lagDeltaker()
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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
    fun `sjekkUtfall - endret sluttarsak`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET, aarsakType = DeltakerStatus.Aarsak.Type.SYK),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

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
    fun `sjekkUtfall - avslutt deltakelse`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - avslutt deltakelse i fremtiden - deltaker får ny sluttdato, fremtidig status`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `sjekkUtfall - har sluttet, avslutt deltakelse i fremtiden - ny sluttdato, fremtidig status`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(deltakerResult) {
            status.type shouldBe DeltakerStatus.Type.DELTAR
            status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
            sluttdato shouldBe endringsrequest.sluttdato
        }

        assertSoftly(nesteStatus.shouldNotBeNull()) {
            type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
            gyldigTil shouldBe null
        }
    }

    @Test
    fun `sjekkUtfall - har sluttet, avslutt deltakelse i fortid - returnerer deltaker med ny sluttdato`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
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

        assertSoftly(deltakerResult) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
            sluttdato shouldBe endringsrequest.sluttdato
        }

        nesteStatus shouldBe null
    }

    @Test
    fun `sjekkUtfall - endre avslutning til fullfort`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            sluttdato = LocalDate.now().minusDays(3),
            deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(aarsak = EndringAarsak.FattJobb, harDeltatt = true, harFullfort = true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = true,
            sluttdato = LocalDate.now().minusDays(1),
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
    fun `sjekkUtfall - endre avslutning til avbrutt`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
            deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, false),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = false,
            sluttdato = LocalDate.now().minusDays(1),
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
    fun `sjekkUtfall - endre avslutning ingen endring - gir erVellykket false`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = null,
            begrunnelse = "begrunnelse",
            harFullfort = true,
            sluttdato = deltaker.sluttdato,
            forslagId = forslag.id,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe false
    }

    @Test
    fun `sjekkUtfall - reaktiver deltakelse lopende oppstart`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = lagDeltakerlisteMedDirekteVedtak(),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato shouldBe null
            sluttdato shouldBe null
        }
    }

    @Test
    fun `sjekkUtfall - reaktiver deltakelse felles oppstart`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = lagDeltakerlisteMedTrengerGodkjenning(),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true

        assertSoftly(resultat.getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.SOKT_INN
            startdato shouldBe null
            sluttdato shouldBe null
        }
    }

    @Test
    fun `sjekkUtfall - endre oppstart når avbrutt endrer ikke status til fullført`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            deltakerliste = lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusMonths(2),
            sluttdato = null,
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerEndringHandler =
            DeltakerEndringHandler(deltaker, endringsrequest.toDeltakerEndringEndring(), deltakerHistorikkServiceMock)
        val resultat = deltakerEndringHandler.sjekkUtfall()

        resultat.erVellykket shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.startdato shouldBe endringsrequest.startdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT
    }
}
