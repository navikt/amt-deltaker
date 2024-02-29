package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.api.model.OppdaterDeltakerRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV2MapperService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import no.nav.amt.deltaker.kafka.utils.SingletonKafkaProvider
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.data.toDeltakerVedVedtak
import no.nav.amt.deltaker.utils.mockAmtPersonServiceClientNavAnsatt
import no.nav.amt.deltaker.utils.mockAmtPersonServiceClientNavEnhet
import no.nav.amt.deltaker.utils.shouldBeCloseTo
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerServiceTest {
    companion object {
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonServiceClientNavAnsatt())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClientNavEnhet())
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val vedtakRepository = VedtakRepository()
        private val deltakerHistorikkService = DeltakerHistorikkService(deltakerEndringRepository, vedtakRepository)
        private val deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerEndringRepository = deltakerEndringRepository,
            vedtakRepository = vedtakRepository,
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            deltakerProducer = DeltakerProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost()), deltakerV2MapperService),
        )

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
    fun `oppdaterDeltaker - deltaker finnes, endring fra utkast til venter pa oppstart - oppdaterer`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = null,
        )
        TestRepository.insert(deltaker, vedtak)

        val vedtaksinformasjon = OppdaterDeltakerRequest.Vedtaksinformasjon(
            id = vedtak.id,
            fattet = LocalDateTime.now(),
            gyldigTil = null,
            deltakerVedVedtak = deltaker.copy(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            ).toDeltakerVedVedtak(),
            fattetAvNav = false,
            opprettet = vedtak.opprettet,
            opprettetAv = sistEndretAv.navIdent,
            opprettetAvEnhet = sistEndretAvEnhet.enhetsnummer,
            sistEndret = LocalDateTime.now(),
            sistEndretAv = sistEndretAv.navIdent,
            sistEndretAvEnhet = sistEndretAvEnhet.enhetsnummer,
        )
        val oppdaterDeltakerRequest = deltaker.copy(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
            .tilOppdaterDeltakerRequest(
                vedtaksinformasjon = vedtaksinformasjon,
                deltakerEndring = null,
            )

        runBlocking {
            deltakerService.oppdaterDeltaker(oppdaterDeltakerRequest)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            deltakerFraDb.vedtaksinformasjon?.fattet shouldBeCloseTo vedtaksinformasjon.fattet

            val deltakerEndringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)
            deltakerEndringFraDb.size shouldBe 0
        }
    }

    @Test
    fun `oppdaterDeltaker - deltaker finnes, forlengelse - oppdaterer`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            sluttdato = LocalDate.now().plusDays(1),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            vedtaksinformasjon = TestData.lagVedtaksinformasjon(
                opprettetAv = sistEndretAv,
                opprettetAvEnhet = sistEndretAvEnhet,
                fattet = LocalDateTime.now().minusDays(3),
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now().minusDays(3),
        )

        TestRepository.insert(deltaker, vedtak)

        val nySluttdato = LocalDate.now().plusWeeks(3)
        val deltakerEndring = OppdaterDeltakerRequest.DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            endringstype = DeltakerEndring.Endringstype.FORLENGELSE,
            endring = DeltakerEndring.Endring.ForlengDeltakelse(
                sluttdato = nySluttdato,
            ),
            endretAv = sistEndretAv.navIdent,
            endretAvEnhet = sistEndretAvEnhet.enhetsnummer,
            endret = LocalDateTime.now(),
        )
        val oppdaterDeltakerRequest = deltaker.copy(sluttdato = nySluttdato)
            .tilOppdaterDeltakerRequest(
                vedtaksinformasjon = null,
                deltakerEndring = deltakerEndring,
            )

        runBlocking {
            deltakerService.oppdaterDeltaker(oppdaterDeltakerRequest)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.sluttdato shouldBe nySluttdato
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerFraDb.vedtaksinformasjon?.fattet shouldBeCloseTo deltaker.vedtaksinformasjon?.fattet
            deltakerFraDb.vedtaksinformasjon?.sistEndretAv shouldBe deltaker.vedtaksinformasjon?.sistEndretAv

            val deltakerEndringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)
            deltakerEndringFraDb.size shouldBe 1
            deltakerEndringFraDb.first().endringstype shouldBe DeltakerEndring.Endringstype.FORLENGELSE
        }
    }

    private fun Deltaker.tilOppdaterDeltakerRequest(
        vedtaksinformasjon: OppdaterDeltakerRequest.Vedtaksinformasjon?,
        deltakerEndring: OppdaterDeltakerRequest.DeltakerEndring?,
    ): OppdaterDeltakerRequest {
        return OppdaterDeltakerRequest(
            id = id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            innhold = innhold,
            status = status,
            vedtaksinformasjon = vedtaksinformasjon,
            deltakerEndring = deltakerEndring,
        )
    }
}
