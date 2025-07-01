package no.nav.amt.deltaker.testdata

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingConsumer
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerDtoMapperService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.deltaker.utils.mockIsOppfolgingstilfelleClient
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class TestdataServiceTest {
    companion object {
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient(), navEnhetService)
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val isOppfolgingstilfelleClient = mockIsOppfolgingstilfelleClient()
        private val forslagRepository = ForslagRepository()
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val endringFraArrangorRepository = EndringFraArrangorRepository()
        private val vedtakRepository = VedtakRepository()
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
        private val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
        private val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
        private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()
        private val deltakerHistorikkService =
            DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
                innsokPaaFellesOppstartRepository,
                endringFraTiltakskoordinatorRepository,
                vurderingService = VurderingService(VurderingRepository()),
            )
        private val hendelseService = HendelseService(
            HendelseProducer(kafkaProducer),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
            VurderingService(VurderingRepository()),
        )

        private val vedtakService = VedtakService(vedtakRepository)
        private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, mockk())
        private val endringFraArrangorService = EndringFraArrangorService(
            endringFraArrangorRepository = endringFraArrangorRepository,
            hendelseService = hendelseService,
            deltakerHistorikkService = deltakerHistorikkService,
        )
        private val unleashToggle = mockk<UnleashToggle>(relaxed = true)
        private val deltakerDtoMapperService =
            DeltakerDtoMapperService(navAnsattService, navEnhetService, deltakerHistorikkService, VurderingRepository())
        private val deltakerProducer = DeltakerProducer(
            kafkaProducer,
        )
        private val deltakerV1Producer = DeltakerV1Producer(
            kafkaProducer,
        )
        private val deltakerProducerService =
            DeltakerProducerService(deltakerDtoMapperService, deltakerProducer, deltakerV1Producer, unleashToggle)

        private val arrangorMeldingProducer = ArrangorMeldingProducer(kafkaProducer)

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
            deltakerEndringService = DeltakerEndringService(
                deltakerEndringRepository,
                navAnsattService,
                navEnhetService,
                hendelseService,
                forslagService,
                deltakerHistorikkService,
            ),
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            endringFraArrangorService = endringFraArrangorService,
            forslagService = forslagService,
            importertFraArenaRepository = importertFraArenaRepository,
            deltakerHistorikkService = deltakerHistorikkService,
            unleashToggle = unleashToggle,
            endringFraTiltakskoordinatorService = mockk(),
            navAnsattService = navAnsattService,
            endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
            navEnhetService = navEnhetService,
        )

        private val deltakerlisteRepository = DeltakerlisteRepository()

        private var pameldingService = PameldingService(
            deltakerService = deltakerService,
            deltakerlisteRepository = deltakerlisteRepository,
            navBrukerService = NavBrukerService(
                NavBrukerRepository(),
                mockAmtPersonClient(),
                navEnhetService,
                navAnsattService,
            ),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            vedtakService = vedtakService,
            isOppfolgingstilfelleClient = isOppfolgingstilfelleClient,
            hendelseService,
            innsokPaaFellesOppstartService,
        )

        private val testdataService = TestdataService(
            pameldingService = pameldingService,
            deltakerlisteRepository = deltakerlisteRepository,
            arrangorMeldingProducer = arrangorMeldingProducer,
            deltakerService = deltakerService,
        )

        val arrangorMeldingConsumer = ArrangorMeldingConsumer(
            forslagService = forslagService,
            deltakerService = deltakerService,
            vurderingService = mockk(),
            unleashToggle = unleashToggle,
            deltakerProducerService = deltakerProducerService,
        )

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        every { unleashToggle.erKometMasterForTiltakstype(any()) } returns true
    }

    @Test
    fun `opprettDeltakelse - deltaker finnes ikke, gyldig request - oppretter ny deltaker`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(
            arrangor = arrangor,
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val opprettetAv = TestData.lagNavAnsatt(navIdent = TESTVEILEDER)
        val opprettetAvEnhet = TestData.lagNavEnhet(enhetsnummer = TESTENHET)
        TestRepository.insert(opprettetAv)
        TestRepository.insert(opprettetAv)
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)

        mockResponses(opprettetAvEnhet, opprettetAv, navBruker)
        TestRepository.insert(deltakerliste)

        val opprettTestDeltakelseRequest = OpprettTestDeltakelseRequest(
            personident = navBruker.personident,
            deltakerlisteId = deltakerliste.id,
            startdato = LocalDate.now().minusDays(1),
            deltakelsesprosent = 60,
            dagerPerUke = 3,
        )

        runBlocking {
            val deltaker = testdataService.opprettDeltakelse(opprettTestDeltakelseRequest)

            arrangorMeldingConsumer.consume(
                deltaker.id,
                objectMapper.writeValueAsString(
                    testdataService.getEndringFraArrangor(
                        deltaker.id,
                        opprettTestDeltakelseRequest.startdato,
                        opprettTestDeltakelseRequest.startdato.plusMonths(3),
                    ),
                ),
            )

            val deltakerFraDb = deltakerService.getDeltakelserForPerson(navBruker.personident, deltakerliste.id).first()
            deltakerFraDb.id shouldBe deltaker.id
            deltakerFraDb.deltakerliste.id shouldBe deltakerliste.id
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerFraDb.startdato shouldBe opprettTestDeltakelseRequest.startdato
            deltakerFraDb.sluttdato shouldBe opprettTestDeltakelseRequest.startdato.plusMonths(3)
            deltakerFraDb.dagerPerUke shouldBe opprettTestDeltakelseRequest.dagerPerUke?.toFloat()
            deltakerFraDb.deltakelsesprosent shouldBe opprettTestDeltakelseRequest.deltakelsesprosent?.toFloat()
            deltakerFraDb.bakgrunnsinformasjon shouldBe null
            deltakerFraDb.deltakelsesinnhold?.ledetekst shouldBe deltakerliste.tiltakstype.innhold!!.ledetekst
            deltakerFraDb.deltakelsesinnhold?.innhold?.size shouldBe 1
        }
    }

    private fun mockResponses(
        navEnhet: NavEnhet,
        navAnsatt: NavAnsatt,
        navBruker: NavBruker,
    ) {
        navAnsatt.navEnhetId?.let { MockResponseHandler.addNavEnhetGetResponse(TestData.lagNavEnhet(it)) }
        MockResponseHandler.addNavEnhetResponse(navEnhet)
        MockResponseHandler.addNavAnsattResponse(navAnsatt)
        MockResponseHandler.addNavBrukerResponse(navBruker)
    }
}
