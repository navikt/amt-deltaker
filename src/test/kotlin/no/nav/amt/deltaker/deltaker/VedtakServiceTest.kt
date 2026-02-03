package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.extensions.getVedtakOrThrow
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class VedtakServiceTest {
    private val vedtakRepository = VedtakRepository()
    private val vedtakService = VedtakService(vedtakRepository)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `innbyggerFattVedtak - ikke fattet vedtak finnes -  fattes`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        insert(vedtak)

        runBlocking {
            val fattetVedtak = vedtakService.innbyggerFattVedtak(deltaker).getVedtakOrThrow()
            assertSoftly(fattetVedtak) {
                id shouldBe vedtak.id
                fattet shouldNotBe null
                fattetAvNav shouldBe false
            }
        }
    }

    @Test
    fun `innbyggerFattVedtak - vedtaket er fattet -  fattes ikke`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
        insert(vedtak)

        vedtakService.innbyggerFattVedtak(deltaker) shouldBe Vedtaksutfall.VedtakAlleredeFattet
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - nytt vedtak - opprettes`() {
        val deltaker = TestData.lagDeltakerKladd()
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(deltaker, endretAvAnsatt, endretAvEnhet)

        runBlocking {
            val vedtak = vedtakService
                .oppdaterEllerOpprettVedtak(
                    deltaker = deltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            assertSoftly(vedtak) {
                deltakerVedVedtak shouldBe deltaker.toDeltakerVedVedtak()
                opprettetAv shouldBe endretAvAnsatt.id
                opprettetAvEnhet shouldBe endretAvEnhet.id
                fattet shouldBe null
                fattetAvNav shouldBe false
                deltakerId shouldBe deltaker.id
            }
        }
    }

    @Test
    fun `navFattVedtak - vedtak finnes, fattes av nav - oppdateres`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val oppdatertDeltaker = TestData.lagDeltakerKladd(id = vedtak.deltakerId).copy(bakgrunnsinformasjon = "Endret bakgrunn")
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

        runBlocking {
            val oppdatertVedtak = vedtakService
                .navFattVedtak(
                    deltaker = oppdatertDeltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            assertSoftly(oppdatertVedtak) {
                deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
                sistEndretAv shouldBe endretAvAnsatt.id
                sistEndretAvEnhet shouldBe endretAvEnhet.id
                fattet shouldBeCloseTo LocalDateTime.now()
                fattetAvNav shouldBe true
                deltakerId shouldBe vedtak.deltakerId
            }
        }
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - vedtak finnes, endres - oppdateres`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val oppdatertDeltaker = TestData.lagDeltakerKladd(id = vedtak.deltakerId).copy(bakgrunnsinformasjon = "Endret bakgrunn")
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

        runBlocking {
            val oppdatertVedtak = vedtakService
                .oppdaterEllerOpprettVedtak(
                    deltaker = oppdatertDeltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            assertSoftly(oppdatertVedtak) {
                deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
                sistEndretAv shouldBe endretAvAnsatt.id
                sistEndretAvEnhet shouldBe endretAvEnhet.id
                deltakerId shouldBe vedtak.deltakerId
            }
        }
    }

    @Test
    fun `avbrytVedtak - vedtak kan avbrytes - avbrytes`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        insert(vedtak)

        val avbruttAvAnsatt = TestData.lagNavAnsatt()
        val avbryttAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(avbruttAvAnsatt, avbryttAvEnhet)

        runBlocking {
            val avbruttVedtak = vedtakService.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet).getVedtakOrThrow()

            assertSoftly(avbruttVedtak) {
                id shouldBe vedtak.id
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                fattet shouldBe null
                sistEndretAv shouldBe avbruttAvAnsatt.id
                sistEndretAvEnhet shouldBe avbryttAvEnhet.id
            }
        }
    }

    @Test
    fun `avbrytVedtak - vedtak er fattet og kan ikk avbrytes - feiler`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
        insert(vedtak)

        val avbruttAvAnsatt = TestData.lagNavAnsatt()
        val avbryttAvEnhet = TestData.lagNavEnhet()

        vedtakService.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet) shouldBe Vedtaksutfall.VedtakAlleredeFattet
    }

    @Test
    fun `navFattVedtak - fatter vedtak`() {
        with(DeltakerContext()) {
            medVedtak(fattet = false)
            withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
            vedtakService.navFattVedtak(deltaker, veileder, navEnhet)::class shouldBe Vedtaksutfall.OK::class

            val deltakersVedtak = vedtakRepository.getForDeltaker(deltaker.id)
            deltakersVedtak.size shouldBe 1

            deltakersVedtak.first().fattet shouldNotBe null
        }
    }

    @Test
    fun `navFattVedtak - mangler vedtak - feiler`() {
        with(DeltakerContext()) {
            withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
            shouldThrow<IllegalStateException> {
                vedtakService.navFattVedtak(deltaker, veileder, navEnhet)
            }
        }
    }

    @Test
    fun `navFattVedtak - vedtak allerede fattet - fatter ikke nytt vedtak`() {
        with(DeltakerContext()) {
            medVedtak(fattet = true)
            withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

            vedtakService.navFattVedtak(deltaker, veileder, navEnhet) shouldBe Vedtaksutfall.VedtakAlleredeFattet

            val deltakersVedtak = vedtakRepository.getForDeltaker(deltaker.id)
            deltakersVedtak.size shouldBe 1

            sammenlignVedtak(deltakersVedtak.first(), vedtak)
        }
    }

    private fun insert(vedtak: Vedtak) {
        TestRepository.insert(TestData.lagNavAnsatt(vedtak.opprettetAv))
        TestRepository.insert(TestData.lagNavEnhet(vedtak.opprettetAvEnhet))
        TestRepository.insert(TestData.lagDeltakerKladd(id = vedtak.deltakerId))
        TestRepository.insert(vedtak)
    }
}
