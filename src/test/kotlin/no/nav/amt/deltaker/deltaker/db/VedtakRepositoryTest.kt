package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class VedtakRepositoryTest {
    private val vedtakRepository = VedtakRepository()

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
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

        vedtakRepository.upsert(vedtak)

        sammenlignVedtak(vedtakRepository.get(vedtak.id)!!, vedtak)
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
        vedtakRepository.upsert(vedtak)

        val oppdatertVedtak = vedtak.copy(fattet = LocalDateTime.now())
        vedtakRepository.upsert(oppdatertVedtak)

        sammenlignVedtak(vedtakRepository.get(vedtak.id)!!, oppdatertVedtak)
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

        vedtakRepository.upsert(vedtak)

        sammenlignVedtak(vedtakRepository.get(vedtak.id)!!, vedtak)
    }
}

fun sammenlignVedtak(first: Vedtak, second: Vedtak) {
    first.id shouldBe second.id
    first.deltakerId shouldBe second.deltakerId
    first.fattet shouldBeCloseTo second.fattet
    first.gyldigTil shouldBeCloseTo second.gyldigTil
    sammenlignDeltakereVedVedtak(first.deltakerVedVedtak, second.deltakerVedVedtak)
    first.fattetAvNav shouldBe second.fattetAvNav
    first.opprettet shouldBeCloseTo second.opprettet
    first.opprettetAv shouldBe second.opprettetAv
    first.opprettetAvEnhet shouldBe second.opprettetAvEnhet
    first.sistEndret shouldBeCloseTo second.sistEndret
    first.sistEndretAv shouldBe second.sistEndretAv
    first.sistEndretAvEnhet shouldBe second.sistEndretAvEnhet
}

fun sammenlignDeltakereVedVedtak(first: DeltakerVedVedtak, second: DeltakerVedVedtak) {
    first.id shouldBe second.id
    first.startdato shouldBe second.startdato
    first.sluttdato shouldBe second.sluttdato
    first.dagerPerUke shouldBe second.dagerPerUke
    first.deltakelsesprosent shouldBe second.deltakelsesprosent
    first.bakgrunnsinformasjon shouldBe second.bakgrunnsinformasjon
    first.deltakelsesinnhold?.ledetekst shouldBe second.deltakelsesinnhold?.ledetekst
    first.deltakelsesinnhold?.innhold shouldBe second.deltakelsesinnhold?.innhold
    first.status.id shouldBe second.status.id
    first.status.type shouldBe second.status.type
    first.status.aarsak shouldBe second.status.aarsak
    first.status.gyldigFra shouldBeCloseTo second.status.gyldigFra
    first.status.gyldigTil shouldBeCloseTo second.status.gyldigTil
    first.status.opprettet shouldBeCloseTo second.status.opprettet
}
