package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.TestOutboxEnvironment
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.OppfolgingstilfellePersonResponse
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProduced
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerV1
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.data.TestRepository.cleanDatabase
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFailsWith

class PameldingServiceTest {
    @BeforeEach
    fun setup() {
        cleanDatabase()
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalDelesMedEksterne(any<Tiltakskode>()) } returns true
    }

    @Test
    fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`(): Unit = runTest {
        val expectedDeltaker = lagDeltaker(
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(expectedDeltaker)

        val actualDeltaker = pameldingService.opprettDeltaker(
            expectedDeltaker.deltakerliste.id,
            expectedDeltaker.navBruker.personident,
        )

        actualDeltaker.id shouldBe expectedDeltaker.id
    }

    @Test
    fun `opprettKladd - deltaker finnes ikke - oppretter ny deltaker`() {
        val arrangor = lagArrangor()
        val deltakerListe = lagDeltakerliste(arrangor = arrangor)
        val opprettetAv = lagNavAnsatt()
        val opprettetAvEnhet = lagNavEnhet()
        val navBruker = lagNavBruker(
            navVeilederId = opprettetAv.id,
            navEnhetId = opprettetAvEnhet.id,
        )

        mockResponses(opprettetAvEnhet, opprettetAv, navBruker)
        TestRepository.insert(deltakerListe)

        runTest {
            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltakerRepository.getFlereForPerson(navBruker.personident, deltakerListe.id).first().id
                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold!!.ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }
    }

    @Test
    fun `opprettKladd - ARR, deltaker har situasjonsbetinget inns og sykmeldt - oppretter ny deltaker`() {
        val tiltakstype = TestData.lagTiltakstype(
            tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            innsatsgrupper = setOf(Innsatsgruppe.VARIG_TILPASSET_INNSATS, Innsatsgruppe.SPESIELT_TILPASSET_INNSATS),
        )
        val arrangor = lagArrangor()
        val deltakerListe = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val opprettetAv = lagNavAnsatt()
        val opprettetAvEnhet = lagNavEnhet()
        val navBruker = lagNavBruker(
            navVeilederId = opprettetAv.id,
            navEnhetId = opprettetAvEnhet.id,
            innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS,
        )

        mockResponses(opprettetAvEnhet, opprettetAv, navBruker)
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonResponse(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusDays(1),
                    ),
                ),
            ),
        )
        TestRepository.insert(deltakerListe)

        runTest {
            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = deltakerListe.id,
                personIdent = navBruker.personident,
            )

            assertSoftly(deltaker) {
                id shouldBe deltakerRepository.getFlereForPerson(navBruker.personident, deltakerListe.id).first().id
                it.deltakerliste.id shouldBe deltakerListe.id
                status.type shouldBe DeltakerStatus.Type.KLADD
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold?.ledetekst shouldBe deltakerListe.tiltakstype.innhold!!.ledetekst
                deltakelsesinnhold?.innhold shouldBe emptyList()
            }
        }
    }

    @Test
    fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() {
        val personIdent = TestData.randomIdent()
        runTest {
            assertFailsWith<NoSuchElementException> {
                pameldingService.opprettDeltaker(UUID.randomUUID(), personIdent)
            }
        }
    }

    private fun mockResponses(
        navEnhet: NavEnhet,
        navAnsatt: NavAnsatt,
        navBruker: NavBruker,
    ) {
        navAnsatt.navEnhetId?.let { MockResponseHandler.addNavEnhetGetResponse(lagNavEnhet(it)) }
        MockResponseHandler.addNavEnhetResponse(navEnhet)
        MockResponseHandler.addNavAnsattResponse(navAnsatt)
        MockResponseHandler.addNavBrukerResponse(navBruker)
    }

    @Test
    fun `opprettKladd - deltaker finnes men har sluttet - oppretter ny deltaker`() {
        val deltaker = lagDeltaker(
            sluttdato = LocalDate.now().minusMonths(3),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(deltaker)

        runTest {
            val nyDeltaker =
                pameldingService.opprettDeltaker(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                )

            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }

    @Test
    fun `upsertUtkast - deltaker finnes - oppdaterer deltaker og oppretter vedtak`() {
        val deltaker = lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
        )
        TestRepository.insert(deltaker)
        val sistEndretAv = lagNavAnsatt()
        val sistEndretAvEnhet = lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)

        val utkastRequest = UtkastRequest(
            deltakelsesinnhold = Deltakelsesinnhold("utkastledetekst", listOf(Innhold("Tekst", "kode", true, null))),
            bakgrunnsinformasjon = "Bakgrunn",
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            endretAv = sistEndretAv.navIdent,
            endretAvEnhet = sistEndretAvEnhet.enhetsnummer,
            godkjentAvNav = false,
        )

        runTest {
            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            val vedtak = vedtakRepository.getForDeltaker(deltaker.id).first()
            vedtak.fattet shouldBe null
            vedtak.fattetAvNav shouldBe false
            vedtak.sistEndretAv shouldBe sistEndretAv.id
            vedtak.sistEndretAvEnhet shouldBe sistEndretAvEnhet.id

            assertProducedHendelse(deltaker.id, HendelseType.OpprettUtkast::class)
        }
    }

    @Test
    fun `upsertUtkast - deltaker med lopende oppstart, godkjent av NAV - oppdaterer deltaker og oppretter fattet vedtak`() {
        val deltaker = lagDeltaker(
            deltakerliste = TestData.lagDeltakerlisteMedLopendeOppstart(),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            startdato = null,
            sluttdato = null,
        )
        TestRepository.insert(deltaker)
        val sistEndretAv = lagNavAnsatt()
        val sistEndretAvEnhet = lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)

        val utkastRequest = UtkastRequest(
            deltakelsesinnhold = Deltakelsesinnhold("test", listOf(Innhold("Tekst", "kode", true, null))),
            bakgrunnsinformasjon = "Bakgrunn",
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            endretAv = sistEndretAv.navIdent,
            endretAvEnhet = sistEndretAvEnhet.enhetsnummer,
            godkjentAvNav = true,
        )

        runTest {
            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            val vedtak = vedtakRepository.getForDeltaker(deltaker.id).first()
            vedtak.fattet shouldNotBe null
            vedtak.fattetAvNav shouldBe true
            vedtak.sistEndretAv shouldBe sistEndretAv.id
            vedtak.sistEndretAvEnhet shouldBe sistEndretAvEnhet.id

            innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).isFailure shouldBe true

            assertProducedHendelse(deltaker.id, HendelseType.NavGodkjennUtkast::class)
        }
    }

    @Test
    fun `upsertUtkast - deltaker med felles oppstart, godkjent av NAV - oppdaterer deltaker`() {
        val deltaker = lagDeltaker(
            deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart(),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            startdato = null,
            sluttdato = null,
        )
        TestRepository.insert(deltaker)
        val sistEndretAv = lagNavAnsatt()
        val sistEndretAvEnhet = lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)

        val utkastRequest = UtkastRequest(
            deltakelsesinnhold = Deltakelsesinnhold("test", listOf(Innhold("Tekst", "kode", true, null))),
            bakgrunnsinformasjon = "Bakgrunn",
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            endretAv = sistEndretAv.navIdent,
            endretAvEnhet = sistEndretAvEnhet.enhetsnummer,
            godkjentAvNav = true,
        )

        runTest {
            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.SOKT_INN
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            val vedtak = vedtakRepository.getForDeltaker(deltaker.id).first()
            vedtak.fattet shouldBe null
            vedtak.fattetAvNav shouldBe false

            val innsok = innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).getOrThrow()
            innsok.utkastGodkjentAvNav shouldBe true
            innsok.utkastDelt shouldBe null
            innsok.innsokt shouldBeCloseTo LocalDateTime.now()

            assertProducedHendelse(deltaker.id, HendelseType.NavGodkjennUtkast::class)
        }
    }

    @Test
    fun `avbrytUtkast - utkast finnes - oppdaterer deltaker og vedtak`() {
        val sistEndretAv = lagNavAnsatt()
        val sistEndretAvEnhet = lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            startdato = null,
            sluttdato = null,
        )
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = null,
            gyldigTil = null,
        )
        TestRepository.insert(deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon()), vedtak)

        val avbrytUtkastRequest = AvbrytUtkastRequest(
            avbruttAv = sistEndretAv.navIdent,
            avbruttAvEnhet = sistEndretAvEnhet.enhetsnummer,
        )

        runTest {
            pameldingService.avbrytUtkast(deltaker.id, avbrytUtkastRequest)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            deltakerFraDb.vedtaksinformasjon shouldBe null

            val vedtakFraDb = vedtakRepository.getForDeltaker(deltaker.id).first()
            vedtakFraDb.fattet shouldBe null
            vedtakFraDb.fattetAvNav shouldBe false
            vedtakFraDb.gyldigTil shouldNotBe null
            vedtakFraDb.sistEndretAv shouldBe sistEndretAv.id
            vedtakFraDb.sistEndretAvEnhet shouldBe sistEndretAvEnhet.id
            assertProducedHendelse(deltaker.id, HendelseType.AvbrytUtkast::class)
        }
    }

    @Test
    fun `innbyggerFattVedtak - deltaker med lopende oppstart - vedtak fattes og ny status er godkjent utkast`() = runTest {
        val deltaker = lagDeltaker(
            deltakerliste = TestData.lagDeltakerlisteMedLopendeOppstart(),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        pameldingService.innbyggerGodkjennUtkast(deltaker.id)

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBeCloseTo LocalDateTime.now()

        innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).isFailure shouldBe true
    }

    @Test
    fun `innbyggerFattVedtak - deltaker med felles oppstart - vedtak fattes ikke ny status er sokt inn`() = runTest {
        val deltaker = lagDeltaker(
            deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart(),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        pameldingService.innbyggerGodkjennUtkast(deltaker.id)

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.SOKT_INN
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBe null

        val innsok = innsokPaaFellesOppstartRepository.getForDeltaker(deltaker.id).getOrThrow()
        innsok.utkastGodkjentAvNav shouldBe false
        innsok.utkastDelt shouldNotBe null
        innsok.innsokt shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `innbyggerFattVedtak - vedtak kunne ikke fattes - upserter ikke`() {
        val deltaker = lagDeltaker(
            deltakerliste = TestData.lagDeltakerlisteMedLopendeOppstart(),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker, fattet = LocalDateTime.now())
        val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        shouldThrow<IllegalArgumentException> {
            runTest {
                pameldingService.innbyggerGodkjennUtkast(deltaker.id)
            }
        }

        val ikkeOppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        ikkeOppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
    }

    companion object {
        private val navEnhetRepository = NavEnhetRepository()
        private val navEnhetService = NavEnhetService(navEnhetRepository, mockPersonServiceClient())

        private val navAnsattRepository = NavAnsattRepository()
        private val navAnsattService = NavAnsattService(navAnsattRepository, mockPersonServiceClient(), navEnhetService)

        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val forslagRepository = ForslagRepository()
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val endringFraArrangorRepository = EndringFraArrangorRepository()
        private val vedtakRepository = VedtakRepository()
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val innsokPaaFellesOppstartRepository = InnsokPaaFellesOppstartRepository()
        private val innsokPaaFellesOppstartService = InnsokPaaFellesOppstartService(innsokPaaFellesOppstartRepository)
        private val endringFraTiltaksKoordinatorRepository = EndringFraTiltakskoordinatorRepository()
        private val vurderingRepository = VurderingRepository()

        private val deltakerHistorikkService =
            DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
                innsokPaaFellesOppstartRepository,
                endringFraTiltaksKoordinatorRepository,
                vurderingRepository,
            )
        private val hendelseService = HendelseService(
            HendelseProducer(TestOutboxEnvironment.outboxService),
            navAnsattService,
            navEnhetRepository,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
            VurderingService(VurderingRepository()),
        )

        private val vedtakService = VedtakService(vedtakRepository)
        private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, mockk())
        private val unleashToggle = mockk<UnleashToggle>(relaxed = true)
        private val deltakerKafkaPayloadBuilder =
            DeltakerKafkaPayloadBuilder(navAnsattRepository, navEnhetRepository, deltakerHistorikkService, VurderingRepository())

        private val deltakerProducer = DeltakerProducer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
        private val deltakerV1Producer = DeltakerV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)

        private val deltakerProducerService =
            DeltakerProducerService(deltakerKafkaPayloadBuilder, deltakerProducer, deltakerV1Producer, unleashToggle)

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = deltakerProducerService,
            deltakerEndringRepository = deltakerEndringRepository,
            deltakerEndringService = DeltakerEndringService(
                deltakerEndringRepository,
                navAnsattRepository,
                navEnhetRepository,
                hendelseService,
                forslagService,
                deltakerHistorikkService,
            ),
            vedtakRepository = vedtakRepository,
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            endringFraArrangorRepository = endringFraArrangorRepository,
            importertFraArenaRepository = importertFraArenaRepository,
            deltakerHistorikkService = deltakerHistorikkService,
            navAnsattService = navAnsattService,
            endringFraTiltakskoordinatorRepository = endringFraTiltaksKoordinatorRepository,
            navEnhetService = navEnhetService,
            forslagRepository = forslagRepository,
        )

        private var pameldingService = PameldingService(
            deltakerRepository = deltakerRepository,
            deltakerService = deltakerService,
            deltakerListeRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(
                NavBrukerRepository(),
                mockPersonServiceClient(),
                navEnhetService,
                navAnsattService,
            ),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            innsokPaaFellesOppstartService = innsokPaaFellesOppstartService,
        )

        @JvmStatic
        @BeforeAll
        fun setupAll() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
        }
    }
}
