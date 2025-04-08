package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class VedtakServiceTest {
    init {
        SingletonPostgres16Container
    }

    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient())
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val deltakerHistorikkService =
        DeltakerHistorikkService(
            DeltakerEndringRepository(),
            VedtakRepository(),
            ForslagRepository(),
            EndringFraArrangorRepository(),
            ImportertFraArenaRepository(),
            InnsokRepository(),
        )
    private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
    private val hendelseService = HendelseService(
        HendelseProducer(kafkaProducer),
        navAnsattService,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
    )

    private val repository = VedtakRepository()
    private val service = VedtakService(repository, hendelseService)

    @Test
    fun `innbyggerFattVedtak - ikke fattet vedtak finnes -  fattes`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        insert(vedtak)

        runBlocking {
            val fattetVedtak = service.innbyggerFattVedtak(deltaker)
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

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.innbyggerFattVedtak(deltaker)
            }
        }
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - nytt vedtak - opprettes`() {
        val deltaker = TestData.lagDeltakerKladd()
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(deltaker, endretAvAnsatt, endretAvEnhet)

        runBlocking {
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
    }

    @Test
    fun `oppdaterEllerOpprettVedtak - vedtak finnes, fattes av nav - oppdateres`() {
        val vedtak = TestData.lagVedtak()
        insert(vedtak)

        val oppdatertDeltaker = TestData.lagDeltakerKladd(id = vedtak.deltakerId).copy(bakgrunnsinformasjon = "Endret bakgrunn")
        val endretAvAnsatt = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

        runBlocking {
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
            val oppdatertVedtak = service.oppdaterEllerOpprettVedtak(
                deltaker = oppdatertDeltaker,
                endretAv = endretAvAnsatt,
                endretAvEnhet = endretAvEnhet,
                fattet = false,
                fattetAvNav = false,
            )

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
            val avbruttVedtak = service.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet)

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

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.avbrytVedtak(deltaker, avbruttAvAnsatt, avbryttAvEnhet)
            }
        }
    }

    private fun insert(vedtak: Vedtak) {
        TestRepository.insert(TestData.lagNavAnsatt(vedtak.opprettetAv))
        TestRepository.insert(TestData.lagNavEnhet(vedtak.opprettetAvEnhet))
        TestRepository.insert(TestData.lagDeltakerKladd(id = vedtak.deltakerId))
        TestRepository.insert(vedtak)
    }
}
