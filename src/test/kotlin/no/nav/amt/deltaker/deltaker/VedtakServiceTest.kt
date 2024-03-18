package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeCloseTo
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class VedtakServiceTest {
    init {
        SingletonPostgresContainer.start()
    }

    private val service = VedtakService(VedtakRepository())

    @Test
    fun `fattVedtak - ikke fattet vedtak finnes -  fattes`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val fattetVedtak = service.fattVedtak(vedtak.id)
        fattetVedtak.id shouldBe vedtak.id
        fattetVedtak.fattet shouldNotBe null
        fattetVedtak.fattetAvNav shouldBe false
    }

    @Test
    fun `fattVedtak - vedtaket er fattet -  fattes ikke`() {
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now())
        insert(vedtak)

        assertThrows(IllegalArgumentException::class.java) {
            service.fattVedtak(vedtak.id)
        }
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - nytt vedtak - opprettes`() {
        val deltaker = TestData.lagDeltakerKladd()
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(deltaker, endretAvAnsatt, endretAvEnhet)

        val vedtak = service.oppdaterEllerOpprettVedtak(
            deltaker = deltaker,
            endretAv = endretAvAnsatt,
            endretAvEnhet = endretAvEnhet,
            fattet = false,
            fattetAvNav = false,
        )

        vedtak.deltakerVedVedtak shouldBe deltaker.toDeltakerVedVedtak()
        vedtak.opprettetAv shouldBe endretAvAnsatt.id
        vedtak.opprettetAvEnhet shouldBe endretAvEnhet.id
        vedtak.fattet shouldBe null
        vedtak.fattetAvNav shouldBe false
        vedtak.deltakerId shouldBe deltaker.id
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - vedtak finnes - oppdateres`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val oppdatertDeltaker = TestData.lagDeltakerKladd(id = vedtak.deltakerId).copy(bakgrunnsinformasjon = "Endret bakgrunn")
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

        val oppdatertVedtak = service.oppdaterEllerOpprettVedtak(
            deltaker = oppdatertDeltaker,
            endretAv = endretAvAnsatt,
            endretAvEnhet = endretAvEnhet,
            fattet = true,
            fattetAvNav = true,
        )

        oppdatertVedtak.deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
        oppdatertVedtak.sistEndretAv shouldBe endretAvAnsatt.id
        oppdatertVedtak.sistEndretAvEnhet shouldBe endretAvEnhet.id
        oppdatertVedtak.fattet shouldBeCloseTo LocalDateTime.now()
        oppdatertVedtak.fattetAvNav shouldBe true
        oppdatertVedtak.deltakerId shouldBe vedtak.deltakerId
    }

    @Test
    fun `avbrytVedtak - vedtak kan avbrytes - avbrytes`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val avbruttAvAnsatt = TestData.lagNavAnsatt()
        val avbryttAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(avbruttAvAnsatt, avbryttAvEnhet)

        val avbruttVedtak = service.avbrytVedtak(vedtak.deltakerId, avbruttAvAnsatt, avbryttAvEnhet)

        avbruttVedtak.id shouldBe vedtak.id
        avbruttVedtak.gyldigTil shouldBeCloseTo LocalDateTime.now()
        avbruttVedtak.fattet shouldBe null
        avbruttVedtak.sistEndretAv shouldBe avbruttAvAnsatt.id
        avbruttVedtak.sistEndretAvEnhet shouldBe avbryttAvEnhet.id
    }

    @Test
    fun `avbrytVedtak - vedtak er fattet og kan ikk avbrytes - feiler`() {
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now())
        insert(vedtak)

        val avbruttAvAnsatt = TestData.lagNavAnsatt()
        val avbryttAvEnhet = TestData.lagNavEnhet()

        assertThrows(IllegalArgumentException::class.java) {
            service.avbrytVedtak(vedtak.deltakerId, avbruttAvAnsatt, avbryttAvEnhet)
        }
    }

    private fun insert(vedtak: Vedtak) {
        TestRepository.insert(TestData.lagNavAnsatt(vedtak.opprettetAv))
        TestRepository.insert(TestData.lagNavEnhet(vedtak.opprettetAvEnhet))
        TestRepository.insert(TestData.lagDeltakerKladd(id = vedtak.deltakerId))
        TestRepository.insert(vedtak)
    }
}
