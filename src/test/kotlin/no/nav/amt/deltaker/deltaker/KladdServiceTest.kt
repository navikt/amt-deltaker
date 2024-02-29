package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
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

class KladdServiceTest {

    companion object {

        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet())

        private val deltakerService = DeltakerService(
            deltakerRepository = DeltakerRepository(),
            deltakerEndringRepository = DeltakerEndringRepository(),
            vedtakRepository = VedtakRepository(),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            deltakerProducer = mockk<DeltakerProducer>(relaxed = true),
        )

        private var kladdService = KladdService(
            deltakerService = deltakerService,
            deltakerlisteRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(NavBrukerRepository(), mockAmtPersonServiceClientNavBruker(), navEnhetService, navAnsattService),
        )

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    private fun mockKladdService(navBruker: NavBruker, navAnsatt: NavAnsatt, navEnhet: NavEnhet) {
        kladdService = KladdService(
            deltakerService,
            deltakerlisteRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(
                NavBrukerRepository(),
                mockAmtPersonServiceClientNavBruker(navBruker),
                NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet(navEnhet)),
                NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt(navAnsatt)),
            ),
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
        mockKladdService(navBruker, opprettetAv, opprettetAvEnhet)

        runBlocking {
            val deltaker = kladdService.opprettKladd(
                deltakerlisteId = deltakerliste.id,
                personident = navBruker.personident,
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
        }
    }

    @Test
    fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() {
        val personident = TestData.randomIdent()
        val navBruker = TestData.lagNavBruker()
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        mockKladdService(navBruker, opprettetAv, opprettetAvEnhet)
        runBlocking {
            assertFailsWith<NoSuchElementException> {
                kladdService.opprettKladd(UUID.randomUUID(), personident)
            }
        }
    }

    @Test
    fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`() {
        val deltaker = TestData.lagDeltaker(
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)

        runBlocking {
            val eksisterendeDeltaker =
                kladdService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
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
        val deltaker = TestData.lagDeltaker(
            sluttdato = LocalDate.now().minusMonths(3),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(deltaker)

        runBlocking {
            val nyDeltaker =
                kladdService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                )

            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }
}
