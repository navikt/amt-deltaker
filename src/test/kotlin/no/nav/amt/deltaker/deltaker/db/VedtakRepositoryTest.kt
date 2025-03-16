package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.vedtak.VedtakRepository
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDateTime

class VedtakRepositoryTest {
    companion object {
        lateinit var repository: VedtakRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            repository = VedtakRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsert - nytt vedtak - inserter`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)

        repository.upsert(vedtak)

        sammenlignVedtak(repository.get(vedtak.id)!!, vedtak)
    }

    @Test
    fun `upsert - oppdatert vedtak - oppdaterer`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)
        repository.upsert(vedtak)

        val oppdatertVedtak = vedtak.copy(fattet = LocalDateTime.now())
        repository.upsert(oppdatertVedtak)

        sammenlignVedtak(repository.get(vedtak.id)!!, oppdatertVedtak)
    }

    @Test
    fun `upsert - vedtak fattet av nav - inserter`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            fattet = LocalDateTime.now(),
            fattetAvNav = true,
        )
        TestRepository.insert(deltaker)

        repository.upsert(vedtak)

        sammenlignVedtak(repository.get(vedtak.id)!!, vedtak)
    }

    @Test
    fun `getIkkeFattet - flere vedtak - henter det som ikke er fattet`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val fattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        val ikkeFattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)

        TestRepository.insert(fattet)
        TestRepository.insert(ikkeFattet)

        sammenlignVedtak(repository.getIkkeFattet(deltaker.id)!!, ikkeFattet)
    }

    @Test
    fun `getIkkeFattet - fattet vedtak - returnerer null`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val fattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)

        TestRepository.insert(fattet)

        repository.getIkkeFattet(deltaker.id) shouldBe null
    }
}

fun sammenlignVedtak(a: Vedtak, b: Vedtak) {
    a.id shouldBe b.id
    a.deltakerId shouldBe b.deltakerId
    a.fattet shouldBeCloseTo b.fattet
    a.gyldigTil shouldBeCloseTo b.gyldigTil
    sammenlignDeltakereVedVedtak(a.deltakerVedVedtak, b.deltakerVedVedtak)
    a.fattetAvNav shouldBe b.fattetAvNav
    a.opprettet shouldBeCloseTo b.opprettet
    a.opprettetAv shouldBe b.opprettetAv
    a.opprettetAvEnhet shouldBe b.opprettetAvEnhet
    a.sistEndret shouldBeCloseTo b.sistEndret
    a.sistEndretAv shouldBe b.sistEndretAv
    a.sistEndretAvEnhet shouldBe b.sistEndretAvEnhet
}

fun sammenlignDeltakereVedVedtak(a: DeltakerVedVedtak, b: DeltakerVedVedtak) {
    a.id shouldBe b.id
    a.startdato shouldBe b.startdato
    a.sluttdato shouldBe b.sluttdato
    a.dagerPerUke shouldBe b.dagerPerUke
    a.deltakelsesprosent shouldBe b.deltakelsesprosent
    a.bakgrunnsinformasjon shouldBe b.bakgrunnsinformasjon
    a.deltakelsesinnhold?.ledetekst shouldBe b.deltakelsesinnhold?.ledetekst
    a.deltakelsesinnhold?.innhold shouldBe b.deltakelsesinnhold?.innhold
    a.status.id shouldBe b.status.id
    a.status.type shouldBe b.status.type
    a.status.aarsak shouldBe b.status.aarsak
    a.status.gyldigFra shouldBeCloseTo b.status.gyldigFra
    a.status.gyldigTil shouldBeCloseTo b.status.gyldigTil
    a.status.opprettet shouldBeCloseTo b.status.opprettet
}
