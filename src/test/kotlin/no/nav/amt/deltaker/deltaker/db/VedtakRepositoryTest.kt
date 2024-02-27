package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerVedVedtak
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.deltaker.model.VedtakDbo
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeCloseTo
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
            SingletonPostgresContainer.start()
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
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt.id,
            sistEndretAvEnhet = navEnhet.id,
        )
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
        )
        TestRepository.insert(deltaker, navAnsatt, navEnhet)

        repository.upsert(vedtak.toDbo(deltaker))

        sammenlignVedtak(repository.get(vedtak.id)!!, vedtak)
    }

    @Test
    fun `upsert - oppdatert vedtak - oppdaterer`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt.id,
            sistEndretAvEnhet = navEnhet.id,
        )
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
        )
        TestRepository.insert(deltaker, navAnsatt, navEnhet)
        repository.upsert(vedtak.toDbo(deltaker))

        val oppdatertVedtak = vedtak.copy(fattet = LocalDateTime.now())
        repository.upsert(oppdatertVedtak.toDbo(deltaker))

        sammenlignVedtak(repository.get(vedtak.id)!!, oppdatertVedtak)
    }

    @Test
    fun `upsert - vedtak fattet av nav - inserter`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt.id,
            sistEndretAvEnhet = navEnhet.id,
        )
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
            fattet = LocalDateTime.now(),
            fattetAvNav = TestData.lagFattetAvNav(fattetAv = navAnsatt.id, fattetAvEnhet = navEnhet.id),
        )
        TestRepository.insert(deltaker, navAnsatt, navEnhet)

        repository.upsert(vedtak.toDbo(deltaker))

        sammenlignVedtak(repository.get(vedtak.id)!!, vedtak)
    }

    @Test
    fun `getIkkeFattet - flere vedtak - henter det som ikke er fattet`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt.id,
            sistEndretAvEnhet = navEnhet.id,
        )
        val fattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
        )
        val ikkeFattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
        )
        TestRepository.insert(deltaker, navAnsatt, navEnhet)

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
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt.id,
            sistEndretAvEnhet = navEnhet.id,
        )
        val fattet: Vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt.id,
            opprettetAvEnhet = navEnhet.id,
        )
        TestRepository.insert(deltaker, navAnsatt, navEnhet)

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

fun Vedtak.toDbo(deltaker: Deltaker) = VedtakDbo(
    id,
    deltakerId,
    fattet,
    gyldigTil,
    deltaker,
    fattetAvNav,
    opprettet,
    opprettetAv,
    opprettetAvEnhet,
    sistEndret,
    sistEndretAv,
    sistEndretAvEnhet,
)

fun sammenlignDeltakereVedVedtak(a: DeltakerVedVedtak, b: DeltakerVedVedtak) {
    a.id shouldBe b.id
    a.startdato shouldBe b.startdato
    a.sluttdato shouldBe b.sluttdato
    a.dagerPerUke shouldBe b.dagerPerUke
    a.deltakelsesprosent shouldBe b.deltakelsesprosent
    a.bakgrunnsinformasjon shouldBe b.bakgrunnsinformasjon
    a.innhold shouldBe b.innhold
    a.status.id shouldBe b.status.id
    a.status.type shouldBe b.status.type
    a.status.aarsak shouldBe b.status.aarsak
    a.status.gyldigFra shouldBeCloseTo b.status.gyldigFra
    a.status.gyldigTil shouldBeCloseTo b.status.gyldigTil
    a.status.opprettet shouldBeCloseTo b.status.opprettet
    a.sistEndretAv shouldBe b.sistEndretAv
    a.sistEndretAvEnhet shouldBe b.sistEndretAvEnhet
    a.sistEndret shouldBeCloseTo b.sistEndret
}
