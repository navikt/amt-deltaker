package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.db.sammenlignDeltakereVedVedtak
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeCloseTo
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDateTime

class DeltakerHistorikkServiceTest {
    companion object {
        private val service = DeltakerHistorikkService(
            DeltakerEndringRepository(),
            VedtakRepository(),
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    @Test
    fun `getForDeltaker - ett vedtak flere endringer - returner liste riktig sortert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(1),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusMonths(1),
        )
        val ikkeFattetVedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = null,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusDays(4),
        )
        val gammelEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(20),
        )
        val nyEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(1),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        TestRepository.insert(ikkeFattetVedtak)
        TestRepository.insert(gammelEndring)
        TestRepository.insert(nyEndring)

        val historikk = service.getForDeltaker(deltaker.id)

        historikk.size shouldBe 4
        sammenlignHistorikk(historikk[0], DeltakerHistorikk.Endring(nyEndring))
        sammenlignHistorikk(historikk[1], DeltakerHistorikk.Vedtak(ikkeFattetVedtak))
        sammenlignHistorikk(historikk[2], DeltakerHistorikk.Endring(gammelEndring))
        sammenlignHistorikk(historikk[3], DeltakerHistorikk.Vedtak(vedtak))
    }

    @Test
    fun `getForDeltaker - ingen endringer - returner tom liste`() {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)

        service.getForDeltaker(deltaker.id) shouldBe emptyList()
    }
}

fun sammenlignHistorikk(a: DeltakerHistorikk, b: DeltakerHistorikk) {
    when (a) {
        is DeltakerHistorikk.Endring -> {
            b as DeltakerHistorikk.Endring
            a.endring.id shouldBe b.endring.id
            a.endring.endring shouldBe b.endring.endring
            a.endring.endretAv shouldBe b.endring.endretAv
            a.endring.endretAvEnhet shouldBe b.endring.endretAvEnhet
            a.endring.endret shouldBeCloseTo b.endring.endret
        }

        is DeltakerHistorikk.Vedtak -> {
            b as DeltakerHistorikk.Vedtak
            a.vedtak.id shouldBe b.vedtak.id
            a.vedtak.deltakerId shouldBe b.vedtak.deltakerId
            a.vedtak.fattet shouldBeCloseTo b.vedtak.fattet
            a.vedtak.gyldigTil shouldBeCloseTo b.vedtak.gyldigTil
            sammenlignDeltakereVedVedtak(a.vedtak.deltakerVedVedtak, b.vedtak.deltakerVedVedtak)
            a.vedtak.opprettetAv shouldBe b.vedtak.opprettetAv
            a.vedtak.opprettetAvEnhet shouldBe b.vedtak.opprettetAvEnhet
            a.vedtak.opprettet shouldBeCloseTo b.vedtak.opprettet
        }
    }
}
