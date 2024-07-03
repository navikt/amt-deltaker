package no.nav.amt.deltaker.internal.konvertervedtak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class KonverterVedtakServiceTest {
    companion object {
        lateinit var repository: VedtakOldRepository
        val deltakerService = mockk<DeltakerService>(relaxed = true)
        lateinit var service: KonverterVedtakService
        lateinit var vedtakRepository: VedtakRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = VedtakOldRepository()
            service = KonverterVedtakService(repository, deltakerService)
            vedtakRepository = VedtakRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearMocks(deltakerService)
    }

    @Test
    fun `konverterVedtak - oppdaterer vedtak og produserer til kafka`() {
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltakerliste = TestData.lagDeltakerliste()
        val innholdselement = deltakerliste.tiltakstype.innhold?.innholdselementer?.first()!!
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            innhold = listOf(
                Innhold(
                    tekst = innholdselement.tekst,
                    innholdskode = innholdselement.innholdskode,
                    valgt = true,
                    beskrivelse = null,
                ),
            ),
        )
        val vedtakOld = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        ).toVedtakOld()

        TestRepository.insertAll(navAnsatt, navEnhet, deltaker, vedtakOld)

        val deltakerUtenInnhold = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(
                    innhold = null,
                ),
            ),
        )
        val vedtakOldUtenInnhold = TestData.lagVedtak(
            deltakerVedVedtak = deltakerUtenInnhold,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        ).toVedtakOld()

        TestRepository.insertAll(deltakerUtenInnhold, vedtakOldUtenInnhold)

        runBlocking {
            service.konverterVedtak()

            val vedtak = vedtakRepository.get(vedtakOld.id) ?: throw RuntimeException()
            vedtak.deltakerVedVedtak.deltakelsesinnhold shouldNotBe null
            vedtak.deltakerVedVedtak.deltakelsesinnhold?.ledetekst shouldBe deltakerliste.tiltakstype.innhold?.ledetekst
            vedtak.deltakerVedVedtak.deltakelsesinnhold?.innhold shouldBe deltaker.innhold

            coVerify(exactly = 1) { deltakerService.produserDeltaker(deltaker.id) }

            val vedtakUtenInnhold = vedtakRepository.get(vedtakOldUtenInnhold.id) ?: throw RuntimeException()
            vedtakUtenInnhold.deltakerVedVedtak.deltakelsesinnhold shouldBe null

            coVerify(exactly = 1) { deltakerService.produserDeltaker(deltakerUtenInnhold.id) }
        }
    }
}
