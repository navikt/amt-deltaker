package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.hendelse.model.HendelseType
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import no.nav.amt.deltaker.kafka.utils.SingletonKafkaProvider
import no.nav.amt.deltaker.kafka.utils.assertProducedForslag
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerEndringServiceTest {
    private val amtPersonClient = mockAmtPersonClient()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClient)
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), amtPersonClient)
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val forslagRepository = ForslagRepository()
    private val deltakerHistorikkService = DeltakerHistorikkService(DeltakerEndringRepository(), VedtakRepository(), forslagRepository)
    private val hendelseService = HendelseService(
        HendelseProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
        navAnsattService,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
    )
    private val forslagService = ForslagService(
        forslagRepository,
        ArrangorMeldingProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
    )

    private val deltakerEndringService = DeltakerEndringService(
        repository = DeltakerEndringRepository(),
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        hendelseService = hendelseService,
        forslagService = forslagService,
    )

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
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

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = "Nye opplysninger",
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreBakgrunnsinformasjon)
            .bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon

        assertProducedHendelse(deltaker.id, HendelseType.EndreBakgrunnsinformasjon::class)
    }

    @Test
    fun `upsertEndring - ikke endret bakgrunnsinformasjon - upserter ikke og returnerer failure`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isFailure shouldBe true

        deltakerEndringService.getForDeltaker(deltaker.id).isEmpty() shouldBe true
    }

    @Test
    fun `upsertEndring - endret innhold - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = InnholdRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            innhold = listOf(Innhold("Tekst", "kode", true, null)),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        resultat.getOrThrow().innhold shouldBe endringsrequest.innhold

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreInnhold)
            .innhold shouldBe endringsrequest.innhold
        assertProducedHendelse(deltaker.id, HendelseType.EndreInnhold::class)
    }

    @Test
    fun `upsertEndring - endret deltakelsesmengde - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = DeltakelsesmengdeRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesprosent = 50,
            dagerPerUke = null,
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
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
    }

    @Test
    fun `upsertEndring - endret startdato - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerFraDb.startdato shouldBe endringsrequest.startdato
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
    }

    @Test
    fun `upsertEndring - endret startdato, venter pa oppstart - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerFraDb.startdato shouldBe endringsrequest.startdato
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
    }

    @Test
    fun `upsertEndring - endret start- og sluttdato i fortid, venter pa oppstart - deltaker blir ikke aktuell`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        deltakerFraDb.startdato shouldBe null
        deltakerFraDb.sluttdato shouldBe null

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret start- og sluttdato i fortid, deltar - deltaker blir har sluttet`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerFraDb.startdato shouldBe endringsrequest.startdato
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret start- og sluttdato i fremtid, deltar - deltaker blir venter pa oppstart`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().plusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        deltakerFraDb.startdato shouldBe endringsrequest.startdato
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret start- og sluttdato i fremtid, har sluttet - deltaker blir deltar`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = StartdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            startdato = LocalDate.now().minusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerFraDb.startdato shouldBe endringsrequest.startdato
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .startdato shouldBe endringsrequest.startdato
        (endring.endring as DeltakerEndring.Endring.EndreStartdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret sluttdato - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().minusWeeks(1),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreSluttdato)
            .sluttdato shouldBe endringsrequest.sluttdato
    }

    @Test
    fun `upsertEndring - endret sluttdato frem i tid - upserter endring og returnerer deltaker med status deltar`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val oppdatertDeltaker = resultat.getOrThrow()
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreSluttdato)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.EndreSluttdato::class)
    }

    @Test
    fun `upsertEndring - endret sluttarsak - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.SYK),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = SluttarsakRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreSluttarsak)
            .aarsak shouldBe endringsrequest.aarsak

        assertProducedHendelse(deltaker.id, HendelseType.EndreSluttarsak::class)
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

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR

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

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = IkkeAktuellRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        deltakerFraDb.startdato shouldBe null
        deltakerFraDb.sluttdato shouldBe null

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.IkkeAktuell)
            .aarsak shouldBe endringsrequest.aarsak

        assertProducedHendelse(deltaker.id, HendelseType.IkkeAktuell::class)
    }

    @Test
    fun `upsertEndring - avslutt deltakelse - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.AvsluttDeltakelse)
            .aarsak shouldBe endringsrequest.aarsak
        (endring.endring as DeltakerEndring.Endring.AvsluttDeltakelse)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.AvsluttDeltakelse::class)
    }

    @Test
    fun `upsertEndring - avslutt deltakelse i fremtiden - returnerer deltaker med ny sluttdato, fremtidig status`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerFraDb.status.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        deltakerFraDb.sluttdato shouldBe endringsrequest.sluttdato

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.AvsluttDeltakelse)
            .aarsak shouldBe endringsrequest.aarsak
        (endring.endring as DeltakerEndring.Endring.AvsluttDeltakelse)
            .sluttdato shouldBe endringsrequest.sluttdato

        assertProducedHendelse(deltaker.id, HendelseType.AvsluttDeltakelse::class)
    }

    @Test
    fun `upsertEndring - reaktiver deltakelse - upserter endring og returnerer deltaker`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
        )

        val resultat = deltakerEndringService.upsertEndring(deltaker, endringsrequest)

        resultat.isSuccess shouldBe true
        val deltakerFraDb = resultat.getOrThrow()
        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        deltakerFraDb.startdato shouldBe null
        deltakerFraDb.sluttdato shouldBe null

        val endring = deltakerEndringService.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.ReaktiverDeltakelse)
            .reaktivertDato shouldBe LocalDate.now()

        assertProducedHendelse(deltaker.id, HendelseType.ReaktiverDeltakelse::class)
    }
}
