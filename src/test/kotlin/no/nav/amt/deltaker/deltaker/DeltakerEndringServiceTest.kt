package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class DeltakerEndringServiceTest {
    private val amtPersonClient = mockAmtPersonClient()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClient)
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonClient)

    private val deltakerEndringService = DeltakerEndringService(
        repository = DeltakerEndringRepository(),
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsertEndring - endret bakgrunnsinformasjon - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = "Nye opplysninger",
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreBakgrunnsinformasjon)
            .bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon
    }

    @Test
    fun `upsertEndring - ikke endret bakgrunnsinformasjon - upserter ikke og returnerer failure`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isFailure shouldBe true

        deltakerEndringService.getForDeltaker(deltaker.id).isEmpty() shouldBe true
    }

    @Test
    fun `upsertEndring - endret innhold - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = InnholdRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            innhold = listOf(Innhold("Tekst", "kode", true, null)),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().innhold shouldBe endringsrequest.innhold

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreInnhold)
            .innhold shouldBe endringsrequest.innhold
    }

    @Test
    fun `upsertEndring - endret deltakelsesmengde - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = DeltakelsesmengdeRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesprosent = 50,
            dagerPerUke = null,
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
        oppdatertDeltaker.dagerPerUke shouldBe null

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent
        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .dagerPerUke shouldBe endringsrequest.dagerPerUke
    }

    @Test
    fun `upsertEndring - endret startdato - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(1),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().startdato shouldBe endringsrequest.startdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
    }

    @Test
    fun `upsertEndring - endret sluttdato - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().minusWeeks(1),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreSluttdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret sluttarsak - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.SYK))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = SluttarsakRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreSluttarsak)
            .aarsak shouldBe endringsrequest.aarsak
    }

    @Test
    fun `upsertEndring - forleng deltakelse - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.ForlengDeltakelse)
            .sluttdato shouldBe endringsrequest.sluttdato
    }
}
