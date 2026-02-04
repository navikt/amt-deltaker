package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.Vedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class VedtakRepositoryTest {
    private val vedtakRepository = VedtakRepository()

    companion object {
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

        val upsertedVedtak = vedtakRepository.upsert(vedtak)

        sammenlignVedtak(upsertedVedtak, vedtak)
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

        val upsertedVedtak = vedtakRepository.upsert(oppdatertVedtak)

        sammenlignVedtak(upsertedVedtak, oppdatertVedtak)
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

        val upsertedVedtak = vedtakRepository.upsert(vedtak)

        sammenlignVedtak(upsertedVedtak, vedtak)
    }
}
