package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonServiceClientNavAnsatt
import no.nav.amt.deltaker.utils.mockAmtPersonServiceClientNavBruker
import no.nav.amt.deltaker.utils.mockAmtPersonServiceClientNavEnhet
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith

class PameldingServiceTest {

    companion object {

        private val deltakerService = DeltakerService(
            deltakerRepository = DeltakerRepository(),
        )

        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet())

        private var pameldingService = PameldingService(
            deltakerService = deltakerService,
            deltakerlisteRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(NavBrukerRepository(), mockAmtPersonServiceClientNavBruker(), navEnhetService, navAnsattService),
            navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt()),
            navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet()),
        )

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    fun mockPameldingService(navBruker: NavBruker, navAnsatt: NavAnsatt, navEnhet: NavEnhet) {
        pameldingService = PameldingService(
            deltakerService,
            deltakerlisteRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(
                NavBrukerRepository(),
                mockAmtPersonServiceClientNavBruker(navBruker),
                NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet(navEnhet)),
                NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt(navAnsatt)),
            ),
            navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt(navAnsatt)),
            navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet(navEnhet)),
        )
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `opprettKladd - deltaker finnes ikke - oppretter ny deltaker`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)
        TestRepository.insert(deltakerliste)
        mockPameldingService(navBruker, opprettetAv, opprettetAvEnhet)

        runBlocking {
            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = deltakerliste.id,
                personident = navBruker.personident,
                opprettetAv = opprettetAv.navIdent,
                opprettetAvEnhet = opprettetAvEnhet.enhetsnummer,
            )

            deltaker.id shouldBe deltakerService.getDeltakelser(navBruker.personident, deltakerliste.id).first().id
            deltaker.deltakerliste.id shouldBe deltakerliste.id
            deltaker.deltakerliste.navn shouldBe deltakerliste.navn
            deltaker.deltakerliste.tiltakstype.type shouldBe deltakerliste.tiltakstype.type
            deltaker.deltakerliste.arrangor.navn shouldBe arrangor.navn
            deltaker.deltakerliste.getOppstartstype() shouldBe deltakerliste.getOppstartstype()
            deltaker.status.type shouldBe DeltakerStatus.Type.KLADD
            deltaker.startdato shouldBe null
            deltaker.sluttdato shouldBe null
            deltaker.dagerPerUke shouldBe null
            deltaker.deltakelsesprosent shouldBe null
            deltaker.bakgrunnsinformasjon shouldBe null
            deltaker.innhold shouldBe emptyList()
            deltaker.sistEndretAv shouldBe opprettetAv
            deltaker.sistEndretAvEnhet shouldBe opprettetAvEnhet
        }
    }

    @Test
    fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() {
        val personident = TestData.randomIdent()
        val navBruker = TestData.lagNavBruker()
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        mockPameldingService(navBruker, opprettetAv, opprettetAvEnhet)
        runBlocking {
            assertFailsWith<NoSuchElementException> {
                pameldingService.opprettKladd(UUID.randomUUID(), personident, opprettetAv.navIdent, opprettetAvEnhet.enhetsnummer)
            }
        }
    }

    @Test
    fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sistEndretAv = sistEndretAv.id,
            sistEndretAvEnhet = sistEndretAvEnhet.id,
        )
        TestRepository.insert(deltaker, sistEndretAv, sistEndretAvEnhet)

        runBlocking {
            val eksisterendeDeltaker =
                pameldingService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                    sistEndretAv.navIdent,
                    sistEndretAvEnhet.enhetsnummer,
                )

            eksisterendeDeltaker.id shouldBe deltaker.id
            eksisterendeDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            eksisterendeDeltaker.startdato shouldBe deltaker.startdato
            eksisterendeDeltaker.sluttdato shouldBe deltaker.sluttdato
            eksisterendeDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke
            eksisterendeDeltaker.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            eksisterendeDeltaker.bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon
            eksisterendeDeltaker.innhold shouldBe deltaker.innhold
        }
    }

    @Test
    fun `opprettKladd - deltaker finnes men har sluttet - oppretter ny deltaker`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            sluttdato = LocalDate.now().minusMonths(3),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sistEndretAv = sistEndretAv.id,
            sistEndretAvEnhet = sistEndretAvEnhet.id,
        )
        TestRepository.insert(deltaker, sistEndretAv, sistEndretAvEnhet)

        runBlocking {
            val nyDeltaker =
                pameldingService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                    sistEndretAv.navIdent,
                    sistEndretAvEnhet.enhetsnummer,
                )

            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }
}
