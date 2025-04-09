package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
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
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EndringFraArrangorServiceTest {
    private val amtPersonClient = mockAmtPersonClient()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClient)
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonClient)
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val deltakerRepository = DeltakerRepository()
    private val deltakerHistorikkService = DeltakerHistorikkService(
        DeltakerEndringRepository(),
        VedtakRepository(),
        forslagRepository,
        endringFraArrangorRepository,
        ImportertFraArenaRepository(),
        InnsokPaaFellesOppstartRepository(),
    )
    private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
    private val hendelseService = HendelseService(
        HendelseProducer(kafkaProducer),
        navAnsattService,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
    )
    private val endringFraArrangorService = EndringFraArrangorService(
        endringFraArrangorRepository = endringFraArrangorRepository,
        hendelseService = hendelseService,
        deltakerHistorikkService = deltakerHistorikkService,
    )

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `insertEndring - legg til oppstartsdato, dato ikke passert - inserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)
        val komplettDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        val startdato = LocalDate.now().plusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val resultat = endringFraArrangorService.insertEndring(komplettDeltaker, endringFraArrangor)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().startdato shouldBe startdato
        resultat.getOrThrow().sluttdato shouldBe sluttdato
        resultat.getOrThrow().status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART

        val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
        endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
        endring.endring shouldBe endringFraArrangor.endring

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `insertEndring - legg til oppstartsdato, dato passert - inserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)
        val komplettDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        val startdato = LocalDate.now().minusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val resultat = endringFraArrangorService.insertEndring(komplettDeltaker, endringFraArrangor)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().startdato shouldBe startdato
        resultat.getOrThrow().sluttdato shouldBe sluttdato
        resultat.getOrThrow().status.type shouldBe DeltakerStatus.Type.DELTAR

        val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
        endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
        endring.endring shouldBe endringFraArrangor.endring

        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `insertEndring - legg til oppstartsdato uten sluttdato, dato passert - inserter endring og returnerer deltaker`(): Unit =
        runBlocking {
            val deltaker = TestData.lagDeltaker(
                startdato = null,
                sluttdato = null,
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            val endretAv = TestData.lagNavAnsatt()
            val endretAvEnhet = TestData.lagNavEnhet()

            TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
            val vedtak = TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)
            val komplettDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            val startdato = LocalDate.now().minusDays(2)
            val endringFraArrangor = TestData.lagEndringFraArrangor(
                deltakerId = deltaker.id,
                endring = EndringFraArrangor.LeggTilOppstartsdato(
                    startdato = startdato,
                    sluttdato = null,
                ),
            )

            val resultat = endringFraArrangorService.insertEndring(komplettDeltaker, endringFraArrangor)

            resultat.isSuccess shouldBe true
            resultat.getOrThrow().startdato shouldBe startdato
            resultat.getOrThrow().sluttdato shouldBe null
            resultat.getOrThrow().status.type shouldBe DeltakerStatus.Type.DELTAR

            val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
            endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            endring.endring shouldBe endringFraArrangor.endring

            assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
        }

    @Test
    fun `insertEndring - legg til oppstartsdato uten sluttdato - fjerner ikke eksisterende sluttdato`(): Unit = runBlocking {
        val gammelsluttdato = LocalDate.now().plusDays(2)
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.of(2021, 1, 1),
            sluttdato = gammelsluttdato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)
        val komplettDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        val startdato = LocalDate.of(2021, 1, 2)
        val endringFraArrangor = TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = null,
            ),
        )

        val resultat = endringFraArrangorService.insertEndring(komplettDeltaker, endringFraArrangor)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().startdato shouldBe startdato
        resultat.getOrThrow().sluttdato shouldBe gammelsluttdato
        resultat.getOrThrow().status.type shouldBe DeltakerStatus.Type.DELTAR

        val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
        endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
        (endring.endring as EndringFraArrangor.LeggTilOppstartsdato).sluttdato shouldBe null

        endring.endring shouldBe endringFraArrangor.endring

        val deltakerEtterEndring = deltakerRepository.get(deltaker.id).getOrThrow()

        deltakerEtterEndring.sluttdato shouldBe gammelsluttdato
        assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
    }

    @Test
    fun `insertEndring - legg til oppstartsdato, start- og sluttdato passert - inserter endring og returnerer deltaker`(): Unit =
        runBlocking {
            val deltaker = TestData.lagDeltaker(
                startdato = null,
                sluttdato = null,
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            val endretAv = TestData.lagNavAnsatt()
            val endretAvEnhet = TestData.lagNavEnhet()

            TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
            val vedtak = TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)
            val komplettDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            val startdato = LocalDate.now().minusMonths(2)
            val sluttdato = LocalDate.now().minusDays(5)
            val endringFraArrangor = TestData.lagEndringFraArrangor(
                deltakerId = deltaker.id,
                endring = EndringFraArrangor.LeggTilOppstartsdato(
                    startdato = startdato,
                    sluttdato = sluttdato,
                ),
            )

            val resultat = endringFraArrangorService.insertEndring(komplettDeltaker, endringFraArrangor)

            resultat.isSuccess shouldBe true
            resultat.getOrThrow().startdato shouldBe startdato
            resultat.getOrThrow().sluttdato shouldBe sluttdato
            resultat.getOrThrow().status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET

            val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
            endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            endring.endring shouldBe endringFraArrangor.endring

            assertProducedHendelse(deltaker.id, HendelseType.LeggTilOppstartsdato::class)
        }
}
