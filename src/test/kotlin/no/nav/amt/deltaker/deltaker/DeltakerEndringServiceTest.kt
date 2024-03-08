package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import org.junit.BeforeClass
import org.junit.Test

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
}
