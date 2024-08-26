package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltakelsesinnhold
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerVedVedtak
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgresContainer
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerConsumerTest {
    companion object {
        lateinit var repository: DeltakerRepository
        lateinit var deltakerV2MapperService: DeltakerV2MapperService
        lateinit var navAnsattService: NavAnsattService
        lateinit var navEnhetService: NavEnhetService
        lateinit var deltakerHistorikkService: DeltakerHistorikkService

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = DeltakerRepository()
            navAnsattService = mockk()
            navEnhetService = mockk()
            deltakerHistorikkService = mockk()
            deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `consumeDeltaker - ny deltaker - gir feil`(): Unit = runBlocking {
        val innhold = Deltakelsesinnhold("ledetekst", emptyList())
        val deltaker = TestData.lagDeltaker(innhold = innhold)

        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns listOf(vedtakEndring(deltaker.id))
        every { deltakerHistorikkService.getInnsoktDato(any()) } returns LocalDate.now()
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(deltaker.navBruker.navEnhetId!!) } returns lagNavEnhet()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<UUID>()) } returns lagNavAnsatt()

        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(deltaker)
        val consumer = DeltakerConsumer(repository)

        shouldThrow<NoSuchElementException> {
            consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))
        }
    }

    @Test
    fun `consumeDeltaker - oppdatert deltaker - endrer innhold`(): Unit = runBlocking {
        val innhold = Deltakelsesinnhold(
            "ledetekst",
            listOf(
                Innhold(
                    tekst = "innholdstekst",
                    innholdskode = "innholdskode",
                    valgt = true,
                    beskrivelse = "beskrivelse",
                ),
            ),
        )
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)
        val nyDeltaker = deltaker.copy(deltakelsesinnhold = innhold)

        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns listOf(vedtakEndring(deltaker.id))
        every { deltakerHistorikkService.getInnsoktDato(any()) } returns LocalDate.now()
        coEvery { navEnhetService.hentEllerOpprettNavEnhet(deltaker.navBruker.navEnhetId!!) } returns lagNavEnhet()
        coEvery { navAnsattService.hentEllerOpprettNavAnsatt(any<UUID>()) } returns lagNavAnsatt()

        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(nyDeltaker)
        val consumer = DeltakerConsumer(repository)

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))

        repository.get(deltaker.id).getOrNull() shouldBe nyDeltaker
    }

    fun vedtakEndring(deltakerId: UUID) = DeltakerHistorikk.Vedtak(
        vedtak = Vedtak(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            fattet = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now(),
            deltakerVedVedtak = DeltakerVedVedtak(
                id = UUID.randomUUID(),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now(),
                dagerPerUke = null,
                deltakelsesprosent = null,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
                status = lagDeltakerStatus(),
            ),
            fattetAvNav = true,
            opprettet = LocalDateTime.now(),
            opprettetAv = UUID.randomUUID(),
            opprettetAvEnhet = UUID.randomUUID(),
            sistEndret = LocalDateTime.now(),
            sistEndretAv = UUID.randomUUID(),
            sistEndretAvEnhet = UUID.randomUUID(),
        ),
    )
}
