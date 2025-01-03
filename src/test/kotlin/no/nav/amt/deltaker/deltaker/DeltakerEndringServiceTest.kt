package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.FjernOppstartsdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringEndring
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.db.sammenlignDeltakerEndring
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.hendelse.model.HendelseType
import no.nav.amt.deltaker.kafka.utils.assertProducedForslag
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
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringServiceTest {
    private val amtPersonClient = mockAmtPersonClient()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClient)
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonClient)
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val forslagRepository = ForslagRepository()
    private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        VedtakRepository(),
        forslagRepository,
        EndringFraArrangorRepository(),
        ImportertFraArenaRepository(),
    )
    private val hendelseService = HendelseService(
        HendelseProducer(kafkaProducer),
        navAnsattService,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
    )
    private val forslagService = ForslagService(
        forslagRepository,
        ArrangorMeldingProducer(kafkaProducer),
        DeltakerRepository(),
        mockk(),
    )

    private val deltakerEndringService = DeltakerEndringService(
        deltakerEndringRepository = deltakerEndringRepository,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        hendelseService = hendelseService,
        forslagService = forslagService,
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
    fun `upsertEndring - endret bakgrunnsinformasjon - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val utfall = DeltakerEndringUtfall.VellykketEndring(
            deltaker = deltaker,
        )
        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = "Nye opplysninger",
        )

        deltakerEndringService.upsertEndring(deltaker, endringsrequest.toDeltakerEndringEndring(), utfall, request = endringsrequest)

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreBakgrunnsinformasjon)
            .bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon

        assertProducedHendelse(deltaker.id, HendelseType.EndreBakgrunnsinformasjon::class)
    }

    @Test
    fun `upsertEndring - endret innhold - upserter og returnerer endring`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val utfall = DeltakerEndringUtfall.VellykketEndring(deltaker)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = InnholdRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesinnhold = Deltakelsesinnhold("tekst", listOf(Innhold("Tekst", "kode", true, null))),
        )

        val resultat = deltakerEndringService.upsertEndring(
            deltaker,
            endringsrequest.toDeltakerEndringEndring(),
            utfall,
            endringsrequest,
        )!!.endring
            as DeltakerEndring.Endring.EndreInnhold
        resultat.innhold shouldBe endringsrequest.deltakelsesinnhold.innhold
        resultat.ledetekst shouldBe endringsrequest.deltakelsesinnhold.ledetekst

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreInnhold).innhold shouldBe endringsrequest.deltakelsesinnhold.innhold
        (endring.endring as DeltakerEndring.Endring.EndreInnhold).ledetekst shouldBe endringsrequest.deltakelsesinnhold.ledetekst
        assertProducedHendelse(deltaker.id, HendelseType.EndreInnhold::class)
    }

    @Test
    fun `upsertEndring - forleng deltakelse - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(deltakerId = deltaker.id)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, forslag)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )
        val utfall = DeltakerEndringUtfall.VellykketEndring(deltaker)

        deltakerEndringService.upsertEndring(deltaker, endringsrequest.toDeltakerEndringEndring(), utfall, endringsrequest)

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.ForlengDeltakelse)
            .sluttdato shouldBe endringsrequest.sluttdato
        (endring.endring as DeltakerEndring.Endring.ForlengDeltakelse)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        val forslagFraDb = forslagService.get(forslag.id).getOrThrow()
        (forslagFraDb.status as Forslag.Status.Godkjent).godkjentAv shouldBe Forslag.NavAnsatt(endretAv.id, endretAvEnhet.id)

        assertProducedHendelse(deltaker.id, HendelseType.ForlengDeltakelse::class)
        assertProducedForslag(
            forslag.copy(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(
                        id = endretAv.id,
                        enhetId = endretAvEnhet.id,
                    ),
                    godkjent = LocalDateTime.now(),
                ),
            ),
        )
    }

    @Test
    fun `upsertEndring - ikke aktuell - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(deltakerId = deltaker.id, endring = Forslag.IkkeAktuell(EndringAarsak.FattJobb))
        val utfall = DeltakerEndringUtfall.VellykketEndring(deltaker)
        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, forslag)

        val endringsrequest = IkkeAktuellRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        deltakerEndringService.upsertEndring(deltaker, endringsrequest.toDeltakerEndringEndring(), utfall, endringsrequest)

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.IkkeAktuell)
            .aarsak shouldBe endringsrequest.aarsak
        (endring.endring as DeltakerEndring.Endring.IkkeAktuell)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        val forslagFraDb = forslagService.get(forslag.id).getOrThrow()
        (forslagFraDb.status as Forslag.Status.Godkjent).godkjentAv shouldBe Forslag.NavAnsatt(endretAv.id, endretAvEnhet.id)

        assertProducedHendelse(deltaker.id, HendelseType.IkkeAktuell::class)
        assertProducedForslag(
            forslag.copy(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(
                        id = endretAv.id,
                        enhetId = endretAvEnhet.id,
                    ),
                    godkjent = LocalDateTime.now(),
                ),
            ),
        )
    }

    @Test
    fun `upsertEndring - fjern oppstartsdato - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusDays(3),
            sluttdato = LocalDate.now().plusWeeks(4),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val utfall = DeltakerEndringUtfall.VellykketEndring(deltaker)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = FjernOppstartsdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            begrunnelse = "begrunnelse",
        )

        deltakerEndringService.upsertEndring(deltaker, endringsrequest.toDeltakerEndringEndring(), utfall, endringsrequest)

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.FjernOppstartsdato)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        assertProducedHendelse(deltaker.id, HendelseType.FjernOppstartsdato::class)
    }

    @Test
    fun `behandleLagretEndring - ubehandlet gyldig endring - oppdaterer deltaker og upserter endring med behandlet`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val deltakelsesprosent = 50F
        val dagerPerUke = 3F
        val id = UUID.randomUUID()

        val ubehandletEndring = upsertEndring(
            TestData.lagDeltakerEndring(
                id = id,
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = deltakelsesprosent,
                    dagerPerUke = dagerPerUke,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(1),
            ),
            null,
        )

        val resultat = deltakerEndringService.behandleLagretDeltakelsesmengde(ubehandletEndring, deltaker)

        resultat.erVellykket shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.deltakelsesprosent shouldBe deltakelsesprosent
        oppdatertDeltaker.dagerPerUke shouldBe dagerPerUke

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0
    }

    @Test
    fun `behandleLagretEndring - ubehandlet ugyldig endring - oppdaterer ikke deltaker og upserter endring med behandlet`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now().minusHours(1),
        )

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val ugyldigEndring = upsertEndring(
            TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 90F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(2),
            ),
            null,
        )

        val gyldigEndring = upsertEndring(
            TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 80F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(1),
            ),
            null,
        )

        val resultat = deltakerEndringService.behandleLagretDeltakelsesmengde(ugyldigEndring, deltaker)

        resultat.erUgyldig shouldBe true

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()

        ubehandlete.size shouldBe 1
        sammenlignDeltakerEndring(ubehandlete.first(), gyldigEndring)
    }

    @Test
    fun `behandleLagretEndring - endringen er utført pga endret startdato - oppdaterer ikke deltaker og upserter endring med behandlet`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now().minusWeeks(2),
        )

        val startdato = LocalDate.now().plusWeeks(1)

        val startdatoEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = endretAv.id,
            endretAvEnhet = endretAvEnhet.id,
            endring = DeltakerEndring.Endring.EndreStartdato(
                startdato = startdato,
                sluttdato = null,
                begrunnelse = null,
            ),
            endret = LocalDateTime.now().minusMinutes(2),
        )

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak, startdatoEndring)

        val fremtidigDeltakelsesprosent = 90F
        val fremtidigDagerPerUke = null

        val fremtidigEndring = upsertEndring(
            TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = fremtidigDeltakelsesprosent,
                    dagerPerUke = fremtidigDagerPerUke,
                    gyldigFra = startdato,
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(2),
            ),
            null,
        )

        val resultat = deltakerEndringService.behandleLagretDeltakelsesmengde(
            fremtidigEndring,
            deltaker.copy(
                deltakelsesprosent = fremtidigDeltakelsesprosent,
                dagerPerUke = fremtidigDagerPerUke,
            ), // deltaker skal være oppdatert pga startdatoendringen...
        )
        resultat.erUgyldig shouldBe true

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0

        val deltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder.gjeldende shouldBe fremtidigEndring.toDeltakelsesmengde()
        deltakelsesmengder.nesteGjeldende shouldBe null
    }

    private fun upsertEndring(endring: DeltakerEndring, behandlet: LocalDateTime? = null): DeltakerEndring {
        deltakerEndringRepository.upsert(
            deltakerEndring = endring,
            behandlet = behandlet,
        )
        return deltakerEndringRepository.get(endring.id).getOrThrow()
    }
}
