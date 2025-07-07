package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.db.sammenlignVedtak
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerContext
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class VedtakServiceTest {
    init {
        SingletonPostgres16Container
    }

    private val repository = VedtakRepository()
    private val service = VedtakService(repository)

    @Test
    fun `innbyggerFattVedtak - ikke fattet vedtak finnes -  fattes`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        insert(vedtak)

        runBlocking {
            val fattetVedtak = service.innbyggerFattVedtak(deltaker).getVedtakOrThrow()
            fattetVedtak.id shouldBe vedtak.id
            fattetVedtak.fattet shouldNotBe null
            fattetVedtak.fattetAvNav shouldBe false
        }
    }

    @Test
    fun `innbyggerFattVedtak - vedtaket er fattet -  fattes ikke`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
        insert(vedtak)

        service.innbyggerFattVedtak(deltaker) shouldBe Vedtaksutfall.VedtakAlleredeFattet
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - nytt vedtak - opprettes`() {
        val deltaker = TestData.lagDeltakerKladd()
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(deltaker, endretAvAnsatt, endretAvEnhet)

        runBlocking {
            val vedtak = service
                .oppdaterEllerOpprettVedtak(
                    deltaker = deltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            vedtak.deltakerVedVedtak shouldBe deltaker.toDeltakerVedVedtak()
            vedtak.opprettetAv shouldBe endretAvAnsatt.id
            vedtak.opprettetAvEnhet shouldBe endretAvEnhet.id
            vedtak.fattet shouldBe null
            vedtak.fattetAvNav shouldBe false
            vedtak.deltakerId shouldBe deltaker.id
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
            val oppdatertVedtak = service
                .navFattVedtak(
                    deltaker = oppdatertDeltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            oppdatertVedtak.deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
            oppdatertVedtak.sistEndretAv shouldBe endretAvAnsatt.id
            oppdatertVedtak.sistEndretAvEnhet shouldBe endretAvEnhet.id
            oppdatertVedtak.fattet shouldBeCloseTo LocalDateTime.now()
            oppdatertVedtak.fattetAvNav shouldBe true
            oppdatertVedtak.deltakerId shouldBe vedtak.deltakerId
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
            val oppdatertVedtak = service
                .oppdaterEllerOpprettVedtak(
                    deltaker = oppdatertDeltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                ).getVedtakOrThrow()

            oppdatertVedtak.deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
            oppdatertVedtak.sistEndretAv shouldBe endretAvAnsatt.id
            oppdatertVedtak.sistEndretAvEnhet shouldBe endretAvEnhet.id
            oppdatertVedtak.deltakerId shouldBe vedtak.deltakerId
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
            val avbruttVedtak = service.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet).getVedtakOrThrow()

            avbruttVedtak.id shouldBe vedtak.id
            avbruttVedtak.gyldigTil shouldBeCloseTo LocalDateTime.now()
            avbruttVedtak.fattet shouldBe null
            avbruttVedtak.sistEndretAv shouldBe avbruttAvAnsatt.id
            avbruttVedtak.sistEndretAvEnhet shouldBe avbryttAvEnhet.id
        }
    }

    @Test
    fun `avbrytVedtak - vedtak er fattet og kan ikk avbrytes - feiler`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
        insert(vedtak)

        val avbruttAvAnsatt = TestData.lagNavAnsatt()
        val avbryttAvEnhet = TestData.lagNavEnhet()

        service.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet) shouldBe Vedtaksutfall.VedtakAlleredeFattet
    }

    @Test
    fun `navFattVedtak - fatter vedtak`() {
        with(DeltakerContext()) {
            medVedtak(fattet = false)
            withTiltakstype(Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
            service.navFattVedtak(deltaker, veileder, navEnhet)::class shouldBe Vedtaksutfall.OK::class

            val deltakersVedtak = repository.getForDeltaker(deltaker.id)
            deltakersVedtak.size shouldBe 1

            deltakersVedtak.first().fattet shouldNotBe null
        }
    }

    @Test
    fun `navFattVedtak - mangler vedtak - feiler`() {
        with(DeltakerContext()) {
            withTiltakstype(Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
            shouldThrow<IllegalStateException> {
                service.navFattVedtak(deltaker, veileder, navEnhet)
            }
        }
    }

    @Test
    fun `navFattVedtak - vedtak allerede fattet - fatter ikke nytt vedtak`() {
        with(DeltakerContext()) {
            medVedtak(fattet = true)
            withTiltakstype(Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

            service.navFattVedtak(deltaker, veileder, navEnhet) shouldBe Vedtaksutfall.VedtakAlleredeFattet

            val deltakersVedtak = repository.getForDeltaker(deltaker.id)
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
