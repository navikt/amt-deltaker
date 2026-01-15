package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.DeltakerProgresjonHandler
import no.nav.amt.deltaker.kafka.utils.assertProduced
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerV1
import no.nav.amt.deltaker.kafka.utils.assertProducedFeilregistrert
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorCtx
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerServiceTest {
    companion object {
        private val amtPersonClientMock = mockPersonServiceClient()
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonClientMock)
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClientMock, navEnhetService)
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val vedtakRepository = VedtakRepository()
        private val forslagRepository = ForslagRepository()
        private val endringFraArrangorRepository = EndringFraArrangorRepository()
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
        private val vurderingRepository = VurderingRepository()
        private val vurderingService = VurderingService(vurderingRepository)
        private val deltakerHistorikkService =
            DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
                InnsokPaaFellesOppstartRepository(),
                EndringFraTiltakskoordinatorRepository(),
                vurderingService,
            )
        private val hendelseService = HendelseService(
            HendelseProducer(kafkaProducer),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
            vurderingService,
        )
        private val unleashToggle = mockk<UnleashToggle>()
        private val deltakerKafkaPayloadBuilder =
            DeltakerKafkaPayloadBuilder(navAnsattService, navEnhetService, deltakerHistorikkService, vurderingRepository)
        private val deltakerProducer = DeltakerProducer(
            kafkaProducer,
        )
        private val deltakerV1Producer = DeltakerV1Producer(
            kafkaProducer,
        )
        private val deltakerProducerService =
            DeltakerProducerService(deltakerKafkaPayloadBuilder, deltakerProducer, deltakerV1Producer, unleashToggle)
        private val forslagService = ForslagService(
            forslagRepository,
            ArrangorMeldingProducer(kafkaProducer),
            deltakerRepository,
            deltakerProducerService,
        )
        private val vedtakService = VedtakService(vedtakRepository)
        private val deltakerEndringService =
            DeltakerEndringService(
                deltakerEndringRepository,
                navAnsattService,
                navEnhetService,
                hendelseService,
                forslagService,
                deltakerHistorikkService,
            )
        private val endringFraArrangorService = EndringFraArrangorService(
            endringFraArrangorRepository = endringFraArrangorRepository,
            hendelseService = hendelseService,
            deltakerHistorikkService = deltakerHistorikkService,
        )
        private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()
        private val endringFraTiltakskoordinatorService = EndringFraTiltakskoordinatorService(
            endringFraTiltakskoordinatorRepository,
            navAnsattService,
        )

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
            deltakerEndringService = deltakerEndringService,
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            endringFraArrangorService = endringFraArrangorService,
            forslagService = forslagService,
            importertFraArenaRepository = importertFraArenaRepository,
            deltakerHistorikkService = deltakerHistorikkService,
            endringFraTiltakskoordinatorService = endringFraTiltakskoordinatorService,
            endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            deltakerProgresjonHandler = DeltakerProgresjonHandler(deltakerRepository),
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalDelesMedEksterne(any<Tiltakskode>()) } returns true
    }

    @Test
    fun `upsertDeltaker - deltaker endrer status fra kladd til utkast - oppdaterer og publiserer til kafka`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )
        TestRepository.insert(deltaker)

        val deltakerMedOppdatertStatus = deltaker.copy(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltakerMedOppdatertStatus,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = null,
        )
        TestRepository.insert(vedtak)
        val oppdatertDeltaker = deltakerMedOppdatertStatus.copy(
            vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
        )

        runBlocking {
            val deltakerFraDb = deltakerService.upsertDeltaker(oppdatertDeltaker)
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon?.opprettetAv shouldBe vedtak.opprettetAv

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
        }
    }

    @Test
    fun `upsertDeltaker - oppretter kladd - oppdaterer i db`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        TestRepository.insert(deltakerliste)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)
        TestRepository.insert(navBruker)

        val deltaker = TestData.lagDeltaker(
            navBruker = navBruker,
            deltakerliste = deltakerliste,
            status = lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )

        runBlocking {
            val deltakerFraDb = deltakerService.upsertDeltaker(deltaker)
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.KLADD
            deltakerFraDb.vedtaksinformasjon shouldBe null
        }
    }

    @Test
    fun `upsertEndretDeltaker - ingen endring - upserter ikke`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(sistEndret = LocalDateTime.now().minusDays(2))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        )

        deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerService.get(deltaker.id).getOrThrow().sistEndret shouldBeCloseTo deltaker.sistEndret
        deltakerEndringService.getForDeltaker(deltaker.id).isEmpty() shouldBe true
    }

    @Test
    fun `upsertEndretDeltaker - avslutt i fremtiden - setter fremtidig HAR_SLUTTET`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 2
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id != deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id != deltaker.status.id }.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        statuser.first { it.id != deltaker.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id != deltaker.status.id }.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `upsertEndretDeltaker - avslutt kursdeltaker i fremtiden - setter fremtidig FULLFORT`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
            ),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 2
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id != deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id != deltaker.status.id }.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        statuser.first { it.id != deltaker.status.id }.type shouldBe DeltakerStatus.Type.FULLFORT
        statuser.first { it.id != deltaker.status.id }.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `upsertEndretDeltaker - avslutt i fremtiden, blir forlenget - deaktiverer fremtidig HAR_SLUTTET`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusDays(2),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)
        val fremtidigHarSluttetStatus = lagDeltakerStatus(
            type = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDateTime.now().plusDays(2),
        )
        TestRepository.insert(fremtidigHarSluttetStatus, deltaker.id)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 2
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.gyldigTil shouldNotBe null
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.gyldigFra.toLocalDate() shouldBe LocalDate.now().plusDays(2)
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `upsertEndretDeltaker - har sluttet, skal delta, avslutt i fremtiden - blir DELTAR, fremtidig HAR_SLUTTET`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 3
        val opprinneligStatus = statuser.first { it.id == deltaker.status.id }
        val currentStatus = statuser.first { it.id == oppdatertDeltaker.status.id }
        val nesteStatus = statuser.first { it.id != opprinneligStatus.id && it.id != currentStatus.id }

        opprinneligStatus.gyldigTil shouldBeCloseTo LocalDateTime.now()
        opprinneligStatus.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        currentStatus.gyldigTil shouldBe null
        currentStatus.type shouldBe DeltakerStatus.Type.DELTAR
        nesteStatus.gyldigTil shouldBe null
        nesteStatus.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        nesteStatus.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        nesteStatus.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `upsertEndretDeltaker - endret deltakelsesmengde - upserter endring`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val endringsrequest = DeltakelsesmengdeRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            forslagId = null,
            begrunnelse = "begrunnelse",
            gyldigFra = LocalDate.now(),
        )

        val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        resultat.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
        resultat.dagerPerUke shouldBe null

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
        oppdatertDeltaker.dagerPerUke shouldBe null

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent
        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .dagerPerUke shouldBe endringsrequest.dagerPerUke

        assertProducedHendelse(deltaker.id, HendelseType.EndreDeltakelsesmengde::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - fremtidig deltakelsesmengde - upserter endring, endrer ikke deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val endringsrequest = DeltakelsesmengdeRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            forslagId = null,
            begrunnelse = "begrunnelse",
            gyldigFra = LocalDate.now().plusDays(1),
        )

        val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
        resultat.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
        resultat.dagerPerUke shouldBe deltaker.dagerPerUke

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
        oppdatertDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent
        (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
            .dagerPerUke shouldBe endringsrequest.dagerPerUke

        assertProducedHendelse(deltaker.id, HendelseType.EndreDeltakelsesmengde::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - endret datoer - upserter endring`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
        resultat.startdato shouldBe endringsrequest.startdato
        resultat.sluttdato shouldBe endringsrequest.sluttdato
        resultat.status.type shouldBe DeltakerStatus.Type.DELTAR

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.startdato shouldBe endringsrequest.startdato
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - endret startdato - upserter ny dato og status`(): Unit = runBlocking {
        val deltakersSluttdato = LocalDate.now().plusWeeks(3)
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusDays(3),
            sluttdato = deltakersSluttdato,
        )

        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)
        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(2),
            sluttdato = null,
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
        resultat.startdato shouldBe endringsrequest.startdato
        resultat.sluttdato shouldBe deltakersSluttdato
        resultat.status.type shouldBe DeltakerStatus.Type.DELTAR

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.startdato shouldBe endringsrequest.startdato
        oppdatertDeltaker.sluttdato shouldBe deltakersSluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltakere - sett på venteliste - upserter endring`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste)
        val deltaker2 = TestData.lagDeltaker(deltakerliste = deltakerliste)
        val deltakerIder = listOf(deltaker.id, deltaker2.id)
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)

        TestRepository.insertAll(endretAv, endretAvEnhet, deltaker, deltaker2, innsokt, innsokt2)

        log.info("oppdaterer deltakere med id $deltakerIder")
        val endredeDeltakere = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.SettPaaVenteliste,
            endretAv.navIdent,
        )

        /*
                endredeDeltakere.size shouldBe 2
                endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker shouldBeComparableWith deltaker.copy(
                    status = deltaker.status.copy(type = DeltakerStatus.Type.VENTELISTE),
                    startdato = null,
                    sluttdato = null,
                )
         */

        endredeDeltakere
            .first {
                it.deltaker.id == deltaker2.id
            }.deltaker shouldBeComparableWith deltaker2.copy(
            status = deltaker2.status.copy(type = DeltakerStatus.Type.VENTELISTE),
            startdato = null,
            sluttdato = null,
        )
        /*

                val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
                historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

                val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
                historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

                assertProducedHendelse(deltaker.id, HendelseType.SettPaaVenteliste::class)
                assertProduced(deltaker.id)
                assertProducedDeltakerV1(deltaker.id)
                assertProduced(deltaker2.id)
                assertProducedDeltakerV1(deltaker2.id)
         */
    }

    @Test
    fun `upsertEndretDeltakere - tildel plass feiler på upsert - ruller tilbake endringer på samme deltaker`(): Unit = runBlocking {
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
        )
        val deltaker1Id = UUID.randomUUID()
        val vedtak = TestData.lagVedtak(deltakerId = deltaker1Id, opprettetAvEnhet = endretAvEnhet, opprettetAv = endretAv)
        val deltaker = TestData.lagDeltaker(
            id = deltaker1Id,
            deltakerliste = deltakerliste,
            vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
        )

        val deltaker2 = TestData.lagDeltaker(deltakerliste = deltakerliste)
        val deltakerIder = listOf(deltaker.id, deltaker2.id)
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        TestRepository.insertAll(endretAv, endretAvEnhet, deltaker, deltaker2, innsokt, innsokt2, vedtak)

        val endredeDeltakere = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.TildelPlass,
            endretAv.navIdent,
        )
        endredeDeltakere.size shouldBe 2
        endredeDeltakere
            .first {
                it.deltaker.id == deltaker.id
            }.deltaker shouldBeComparableWith deltaker.copy(
            status = deltaker.status.copy(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
            vedtaksinformasjon = vedtak
                .copy(
                    fattet = LocalDateTime.now(),
                    fattetAvNav = true,
                    sistEndret = LocalDateTime.now(),
                    sistEndretAvEnhet = vedtak.opprettetAvEnhet,
                ).tilVedtaksInformasjon(),
        )
        val ikkeEndretDeltaker = endredeDeltakere.first {
            it.deltaker.id == deltaker2.id
        }
        ikkeEndretDeltaker.deltaker shouldBeComparableWith deltaker2
        ikkeEndretDeltaker.isSuccess shouldBe false
        ikkeEndretDeltaker.exceptionOrNull shouldBe IllegalStateException("Deltaker ${deltaker2.id} mangler et vedtak som kan fattes")

        val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
        historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
        historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 0

        assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltakere - tildel plass - upserter endring, bruker deltakerliste sin start og sluttdato`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            startDato = LocalDate.now().plusDays(2),
            sluttDato = LocalDate.now().plusDays(30),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val deltaker2 = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            deltakerId = deltaker.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )
        val vedtak2 = TestData.lagVedtak(
            deltakerVedVedtak = deltaker2,
            deltakerId = deltaker2.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )
        val deltakerIder = listOf(deltaker.id, deltaker2.id)
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        TestRepository.insertAll(
            endretAv,
            endretAvEnhet,
            deltaker,
            deltaker2,
            innsokt,
            innsokt2,
            vedtak,
            vedtak2,
        )

        val endredeDeltakere = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.TildelPlass,
            endretAv.navIdent,
        )
        endredeDeltakere.size shouldBe 2
        val testdeltaker = endredeDeltakere
            .first {
                it.deltaker.id == deltaker.id
            }.deltaker
        testdeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        testdeltaker.startdato shouldBe deltakerliste.startDato
        testdeltaker.sluttdato shouldBe deltakerliste.sluttDato

        val testdeltaker2 = endredeDeltakere
            .first {
                it.deltaker.id == deltaker2.id
            }.deltaker
        testdeltaker2.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        testdeltaker2.startdato shouldBe deltakerliste.startDato
        testdeltaker2.sluttdato shouldBe deltakerliste.sluttDato

        val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
        historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
        historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProduced(deltaker2.id)
        assertProducedDeltakerV1(deltaker2.id)
    }

    @Test
    fun `upsertEndretDeltakere - tildel plass - upserter endring, dato passert får start og sluttdato null`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            startDato = LocalDate.now().minusDays(2),
            sluttDato = LocalDate.now().plusDays(30),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val deltaker2 = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val deltakerIder = listOf(deltaker.id, deltaker2.id)
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            deltakerId = deltaker.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )
        val vedtak2 = TestData.lagVedtak(
            deltakerVedVedtak = deltaker2,
            deltakerId = deltaker2.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        TestRepository.insertAll(
            endretAv,
            endretAvEnhet,
            deltaker,
            deltaker2,
            innsokt,
            innsokt2,
            vedtak,
            vedtak2,
        )

        val endredeDeltakere = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.TildelPlass,
            endretAv.navIdent,
        )
        endredeDeltakere.size shouldBe 2
        val testdeltaker = endredeDeltakere.first {
            it.deltaker.id == deltaker.id
        }
        testdeltaker.deltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        testdeltaker.deltaker.startdato shouldBe null
        testdeltaker.deltaker.sluttdato shouldBe null

        val testdeltaker2 = endredeDeltakere.first {
            it.deltaker.id == deltaker2.id
        }
        testdeltaker2.deltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        testdeltaker2.deltaker.startdato shouldBe null
        testdeltaker2.deltaker.sluttdato shouldBe null

        val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
        historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
        historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProduced(deltaker2.id)
        assertProducedDeltakerV1(deltaker2.id)
    }

    @Test
    fun `transactionalDeltakerUpsert - ny deltaker - ruller tilbake alle endringer`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            startDato = LocalDate.now().plusDays(2),
            sluttDato = LocalDate.now().plusDays(30),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)

        val upsertResult = deltakerService.transactionalDeltakerUpsert(deltaker) {
            throw RuntimeException("Feiler")
        }

        upsertResult.isFailure shouldBe true
        val getResult = deltakerService.get(deltaker.id)
        getResult.isFailure shouldBe true
    }

    @Test
    fun `transactionalDeltakerUpsert - ny status, siste insert feiler - ruller tilbake alle endringer`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            startDato = LocalDate.now().plusDays(2),
            sluttDato = LocalDate.now().plusDays(30),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            deltakerId = deltaker.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )
        TestRepository.insertAll(
            endretAv,
            endretAvEnhet,
            deltaker,
            vedtak,
        )

        deltakerService.transactionalDeltakerUpsert(
            deltaker.copy(status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)),
        ) {
            throw RuntimeException("Feiler")
        }

        val result = deltakerService.get(deltaker.id).getOrThrow()
        result.status.type shouldBe deltaker.status.type

        val insertedEndring = endringFraTiltakskoordinatorService.getForDeltaker(deltaker.id)
        insertedEndring shouldBe emptyList()
    }

    @Test
    fun `upsertEndretDeltakere - tildel plass feiler på siste deltaker - ruller tilbake en deltaker`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            startDato = LocalDate.now().plusDays(2),
            sluttDato = LocalDate.now().plusDays(30),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet(enhetsnummer = "0326")
        val deltakerInsert = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val deltaker2Insert = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltakerInsert,
            deltakerId = deltakerInsert.id,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            sistEndretAv = endretAv,
            sistEndretAvEnhet = endretAvEnhet,
        )

        val deltakerIder = listOf(deltakerInsert.id, deltaker2Insert.id)
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltakerInsert.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(
            deltakerId = deltaker2Insert.id,
            innsoktAv = endretAv.id,
            innsoktAvEnhet = endretAvEnhet.id,
        )
        TestRepository.insertAll(
            endretAv,
            endretAvEnhet,
            deltakerInsert,
            deltaker2Insert,
            innsokt,
            innsokt2,
            vedtak,
        )
        val deltakereResult = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.TildelPlass,
            endretAv.navIdent,
        )

        deltakereResult.size shouldBe 2
        deltakereResult.filter { it.isSuccess }.size shouldBe 1

        val resDeltaker1 = deltakereResult.first { it.deltaker.id == deltakerInsert.id }.deltaker
        resDeltaker1.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        resDeltaker1.startdato shouldBe deltakerliste.startDato
        resDeltaker1.sluttdato shouldBe deltakerliste.sluttDato

        val resDeltaker2 = deltakereResult.first { it.deltaker.id == deltaker2Insert.id }.deltaker
        resDeltaker2.status.type shouldBe deltaker2Insert.status.type
        resDeltaker2.startdato shouldBe deltaker2Insert.startdato
        resDeltaker2.sluttdato shouldBe deltaker2Insert.sluttdato

        assertProduced(deltakerInsert.id)
        assertProducedDeltakerV1(deltakerInsert.id)
        assertProducedHendelse(deltakerInsert.id, HendelseType.TildelPlass::class)
    }

    @Test
    fun `upsertEndretDeltakere - del med arrangør - inserter endring og returnerer endret deltaker`(): Unit = runBlocking {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(
                tiltakskode =
                    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            ),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(type = DeltakerStatus.Type.SOKT_INN),
        )
        val deltaker2 = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(type = DeltakerStatus.Type.SOKT_INN),
        )

        val deltakerIder = listOf(deltaker.id, deltaker2.id)
        val innsokt = TestData.lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        val innsokt2 = TestData.lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
        TestRepository.insertAll(
            endretAv,
            endretAvEnhet,
            deltaker,
            deltaker2,
            innsokt,
            innsokt2,
        )

        val endredeDeltakere = deltakerService.oppdaterDeltakere(
            deltakerIder,
            EndringFraTiltakskoordinator.DelMedArrangor,
            endretAv.navIdent,
        )
        endredeDeltakere.size shouldBe 2
        val testdeltaker = endredeDeltakere
            .first {
                it.deltaker.id == deltaker.id
            }.deltaker
        testdeltaker.status.type shouldBe DeltakerStatus.Type.SOKT_INN
        testdeltaker.erManueltDeltMedArrangor shouldBe true
        val testdeltaker2 = endredeDeltakere
            .first {
                it.deltaker.id == deltaker2.id
            }.deltaker
        testdeltaker2.status.type shouldBe DeltakerStatus.Type.SOKT_INN
        testdeltaker2.erManueltDeltMedArrangor shouldBe true

        val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
        historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
        historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProduced(deltaker2.id)
        assertProducedDeltakerV1(deltaker2.id)
    }

    @Test
    fun `giAvslag - deltaker får riktig status og historikk`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            medInnsok()

            val avslag = EndringFraTiltakskoordinator.Avslag(
                aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                    type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                    beskrivelse = null,
                ),
                begrunnelse = "Fordi...",
            )
            val deltaker = deltakerService.giAvslag(
                deltaker.id,
                avslag,
                navAnsatt.navIdent,
            )

            val endringer = endringFraTiltakskoordinatorService.getForDeltaker(deltaker.id)
            endringer.size shouldBe 1
            (endringer.first().endring is EndringFraTiltakskoordinator.Avslag) shouldBe true

            deltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            deltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.KURS_FULLT
            deltaker.startdato shouldBe null
            deltaker.sluttdato shouldBe null

            assertProducedHendelse(deltaker.id, HendelseType.Avslag::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
        }
    }

    @Test
    fun `produserDeltakereForPerson - deltaker finnes - publiserer til kafka`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        runBlocking {
            deltakerService.produserDeltakereForPerson(deltaker.navBruker.personident)

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
        }
    }

    @Test
    fun `innbyggerFattVedtak - deltaker har status utkast - oppretter ny status og upserter`() {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        val deltakerMedVedtak = deltakerService.get(deltaker.id).getOrThrow()

        runBlocking {
            deltakerService.innbyggerFattVedtak(deltakerMedVedtak)
        }

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `innbyggerFattVedtak - deltaker har ikke status utkast - upserter uten å endre status`() {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        val deltakerMedVedtak = deltakerService.get(deltaker.id).getOrThrow()

        runBlocking {
            deltakerService.innbyggerFattVedtak(deltakerMedVedtak)
        }

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `innbyggerFattVedtak - vedtak kunne ikke fattes - upserter ikke`() {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker, fattet = LocalDateTime.now())
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                deltakerService.innbyggerFattVedtak(deltaker)
            }
        }

        val ikkeOppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        ikkeOppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
    }

    @Test
    fun `oppdaterSistBesokt - produserer hendelse`() {
        val deltaker = TestData.lagDeltaker()
        val sistBesokt = ZonedDateTime.now()

        TestRepository.insert(deltaker)

        runBlocking {
            deltakerService.oppdaterSistBesokt(deltaker.id, sistBesokt)
        }

        assertProducedHendelse(deltaker.id, HendelseType.DeltakerSistBesokt::class)
    }

    @Test
    fun `feilregistrerDeltaker - deltaker feilregistreres og oppdatert deltaker produseres`() {
        val deltaker = TestData.lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
        )
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak, deltakerEndring)

        runBlocking {
            deltakerService.feilregistrerDeltaker(deltaker.id)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            deltakerFraDb.startdato shouldBe null
            deltakerFraDb.sluttdato shouldBe null
            deltakerFraDb.dagerPerUke shouldBe null
            deltakerFraDb.deltakelsesprosent shouldBe null
            deltakerFraDb.bakgrunnsinformasjon shouldBe null
            deltakerFraDb.deltakelsesinnhold shouldBe null

            assertProducedFeilregistrert(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
        }
    }

    @Test
    fun `avgrensSluttdatoerTil - deltaker har senere sluttdato enn deltakerliste - deltakers sluttdato endres`() {
        val deltakerliste = TestData.lagDeltakerliste()
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = deltakerliste.sluttDato!!.plusMonths(1),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)

        TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

        runBlocking { deltakerService.avgrensSluttdatoerTil(deltakerliste) }

        val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.sluttdato shouldBe deltakerliste.sluttDato
    }

    @Test
    fun `avgrensSluttdatoerTil - deltaker har tidligere sluttdato enn deltakerliste - deltakers sluttdato endres ikke`() {
        val deltakerliste = TestData.lagDeltakerliste()
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = deltakerliste.sluttDato!!.minusDays(1),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)

        TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

        runBlocking { deltakerService.avgrensSluttdatoerTil(deltakerliste) }

        val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.sluttdato shouldNotBe deltakerliste.sluttDato
    }
}

infix fun Deltaker.shouldBeComparableWith(expected: Deltaker?) {
    val statusOpprettetDay = this.status.opprettet
        .toLocalDate()
        .atStartOfDay()
    val gyldigFra = this.status.gyldigFra
        .toLocalDate()
        .atStartOfDay()
    val sistEndret = this.sistEndret.toLocalDate().atStartOfDay()

    fun LocalDateTime.atStartOfDay() = this.toLocalDate().atStartOfDay()

    val now = LocalDateTime.now()
    this.copy(
        sistEndret = sistEndret,
        status = status.copy(id = expected!!.status.id, opprettet = statusOpprettetDay, gyldigFra = gyldigFra),
        opprettet = now,
        vedtaksinformasjon = vedtaksinformasjon?.copy(
            fattet = this.vedtaksinformasjon.fattet?.atStartOfDay(),
            sistEndret = this.vedtaksinformasjon.sistEndret.atStartOfDay()!!,
        ),
    ) shouldBe expected.copy(
        sistEndret = expected.sistEndret.atStartOfDay(),
        status = expected.status.copy(
            id = expected.status.id,
            opprettet = expected.status.opprettet.atStartOfDay(),
            gyldigFra = expected.status.gyldigFra.atStartOfDay(),
        ),
        opprettet = now,
        vedtaksinformasjon = vedtaksinformasjon?.copy(
            fattet = expected.vedtaksinformasjon?.fattet?.atStartOfDay(),
            sistEndret = expected.vedtaksinformasjon?.sistEndret?.atStartOfDay()!!,
        ),
    )
}
