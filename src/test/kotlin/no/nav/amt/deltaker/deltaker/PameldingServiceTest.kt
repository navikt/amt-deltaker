package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.api.model.AvbrytUtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.UtkastRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Innsatsgruppe
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.hendelse.model.HendelseType
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith

class PameldingServiceTest {
    companion object {
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val forslagRepository = ForslagRepository()
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val endringFraArrangorRepository = EndringFraArrangorRepository()
        private val vedtakRepository = VedtakRepository()
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val deltakerHistorikkService =
            DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
            )
        private val hendelseService = HendelseService(
            HendelseProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
        )

        private val vedtakService = VedtakService(vedtakRepository, hendelseService)
        private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, mockk())
        private val endringFraArrangorService = EndringFraArrangorService(endringFraArrangorRepository, hendelseService)

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducerService = mockk(relaxed = true),
            deltakerEndringService = DeltakerEndringService(
                deltakerEndringRepository,
                navAnsattService,
                navEnhetService,
                hendelseService,
                forslagService,
            ),
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            endringFraArrangorService = endringFraArrangorService,
            forslagService = forslagService,
            importertFraArenaRepository = importertFraArenaRepository,
        )

        private var pameldingService = PameldingService(
            deltakerService = deltakerService,
            deltakerlisteRepository = DeltakerlisteRepository(),
            navBrukerService = NavBrukerService(
                NavBrukerRepository(),
                mockAmtPersonClient(),
                navEnhetService,
                navAnsattService,
            ),
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            vedtakService = vedtakService,
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
    }

    @Test
    fun `opprettKladd - deltaker finnes ikke - oppretter ny deltaker`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)

        MockResponseHandler.addNavEnhetResponse(opprettetAvEnhet)
        MockResponseHandler.addNavAnsattResponse(opprettetAv)
        MockResponseHandler.addNavBrukerResponse(navBruker)
        TestRepository.insert(deltakerliste)

        runBlocking {
            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = deltakerliste.id,
                personident = navBruker.personident,
            )

            deltaker.id shouldBe deltakerService.getDeltakelser(navBruker.personident, deltakerliste.id).first().id
            deltaker.deltakerlisteId shouldBe deltakerliste.id
            deltaker.status.type shouldBe DeltakerStatus.Type.KLADD
            deltaker.startdato shouldBe null
            deltaker.sluttdato shouldBe null
            deltaker.dagerPerUke shouldBe null
            deltaker.deltakelsesprosent shouldBe null
            deltaker.bakgrunnsinformasjon shouldBe null
            deltaker.deltakelsesinnhold.ledetekst shouldBe deltakerliste.tiltakstype.innhold!!.ledetekst
            deltaker.deltakelsesinnhold.innhold shouldBe emptyList()
        }
    }

    @Test
    fun `opprettKladd - deltaker har feil innsatsgruppe ift tiltaket - kaster IllegalArgumentException`() {
        val tiltakstype = TestData.lagTiltakstype(
            innsatsgrupper = setOf(Innsatsgruppe.VARIG_TILPASSET_INNSATS, Innsatsgruppe.SPESIELT_TILPASSET_INNSATS),
        )
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(
            navVeilederId = opprettetAv.id,
            navEnhetId = opprettetAvEnhet.id,
            innsatsgruppe = Innsatsgruppe.STANDARD_INNSATS,
        )

        MockResponseHandler.addNavEnhetResponse(opprettetAvEnhet)
        MockResponseHandler.addNavAnsattResponse(opprettetAv)
        MockResponseHandler.addNavBrukerResponse(navBruker)
        TestRepository.insert(deltakerliste)

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                pameldingService.opprettKladd(
                    deltakerlisteId = deltakerliste.id,
                    personident = navBruker.personident,
                )
            }
        }
    }

    @Test
    fun `opprettKladd - deltakerliste finnes ikke - kaster NoSuchElementException`() {
        val personident = TestData.randomIdent()
        runBlocking {
            assertFailsWith<NoSuchElementException> {
                pameldingService.opprettKladd(UUID.randomUUID(), personident)
            }
        }
    }

    @Test
    fun `opprettKladd - deltakerliste er avsluttet - kaster IllegalArgumentException`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, status = Deltakerliste.Status.AVSLUTTET)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)

        MockResponseHandler.addNavEnhetResponse(opprettetAvEnhet)
        MockResponseHandler.addNavAnsattResponse(opprettetAv)
        MockResponseHandler.addNavBrukerResponse(navBruker)
        TestRepository.insert(deltakerliste)

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                pameldingService.opprettKladd(
                    deltakerlisteId = deltakerliste.id,
                    personident = navBruker.personident,
                )
            }
        }
    }

    @Test
    fun `opprettKladd - deltaker finnes og deltar fortsatt - returnerer eksisterende deltaker`() {
        val deltaker = TestData.lagDeltaker(
            sluttdato = null,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)

        runBlocking {
            val eksisterendeDeltaker =
                pameldingService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                )

            eksisterendeDeltaker.id shouldBe deltaker.id
            eksisterendeDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            eksisterendeDeltaker.startdato shouldBe deltaker.startdato
            eksisterendeDeltaker.sluttdato shouldBe deltaker.sluttdato
            eksisterendeDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke
            eksisterendeDeltaker.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            eksisterendeDeltaker.bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon
            eksisterendeDeltaker.deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold
        }
    }

    @Test
    fun `opprettKladd - deltaker finnes men har sluttet - oppretter ny deltaker`() {
        val deltaker = TestData.lagDeltaker(
            sluttdato = LocalDate.now().minusMonths(3),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(deltaker)

        runBlocking {
            val nyDeltaker =
                pameldingService.opprettKladd(
                    deltaker.deltakerliste.id,
                    deltaker.navBruker.personident,
                )

            nyDeltaker.id shouldNotBe deltaker.id
            nyDeltaker.status.type shouldBe DeltakerStatus.Type.KLADD
        }
    }

    @Test
    fun `upsertUtkast - deltaker finnes - oppdaterer deltaker og oppretter vedtak`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
        )
        TestRepository.insert(deltaker)
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
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

        runBlocking {
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
    fun `upsertUtkast - deltaker finnes, godkjent av NAV - oppdaterer deltaker og oppretter fattet vedtak`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            startdato = null,
            sluttdato = null,
        )
        TestRepository.insert(deltaker)
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
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

        runBlocking {
            pameldingService.upsertUtkast(deltaker.id, utkastRequest)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            deltakerFraDb.vedtaksinformasjon shouldNotBe null

            val vedtak = vedtakRepository.getForDeltaker(deltaker.id).first()
            vedtak.fattet shouldNotBe null
            vedtak.fattetAvNav shouldBe true
            vedtak.sistEndretAv shouldBe sistEndretAv.id
            vedtak.sistEndretAvEnhet shouldBe sistEndretAvEnhet.id
            assertProducedHendelse(deltaker.id, HendelseType.NavGodkjennUtkast::class)
        }
    }

    @Test
    fun `avbrytUtkast - utkast finnes - oppdaterer deltaker og vedtak`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
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
        TestRepository.insert(deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksinformasjon()), vedtak)

        val avbrytUtkastRequest = AvbrytUtkastRequest(
            avbruttAv = sistEndretAv.navIdent,
            avbruttAvEnhet = sistEndretAvEnhet.enhetsnummer,
        )

        runBlocking {
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
}
