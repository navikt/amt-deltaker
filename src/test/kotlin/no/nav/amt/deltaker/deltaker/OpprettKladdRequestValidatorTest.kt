package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.api.model.request.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltakerliste.DeltakerListeRepository
import no.nav.amt.deltaker.deltakerliste.Deltakerliste.Status
import no.nav.amt.deltaker.isoppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerListe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Year
import java.util.UUID

class OpprettKladdRequestValidatorTest {
    private val deltakerRepository: DeltakerRepository = mockk(relaxed = true)
    private val deltakerListeRepository: DeltakerListeRepository = mockk(relaxed = true)
    private val brukerService: NavBrukerService = mockk(relaxed = true)
    private val personServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val isOppfolgingsTilfelleClient: IsOppfolgingstilfelleClient = mockk(relaxed = true)

    private val sut = OpprettKladdRequestValidator(
        deltakerRepository = deltakerRepository,
        deltakerListeRepository = deltakerListeRepository,
        brukerService = brukerService,
        personServiceClient = personServiceClient,
        isOppfolgingsTilfelleClient = isOppfolgingsTilfelleClient,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)),
        )

        every { deltakerRepository.getMany(any(), any()) } returns emptyList()

        coEvery { brukerService.get(any()) } returns Result.success(lagNavBruker())

        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(20).value
    }

    @Test
    fun `validateRequest - ingen feil - skal returnere Valid`(): Unit = runBlocking {
        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    @Test
    fun `validateRequest - deltaker finnes og deltar allerede - skal returnere Invalid`(): Unit = runBlocking {
        every {
            deltakerRepository.getMany(any(), any())
        } returns
            listOf(lagDeltaker(status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR)))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltakeren er allerede opprettet og deltar fortsatt")
    }

    @ParameterizedTest
    @EnumSource(value = Status::class, names = ["AVBRUTT", "AVLYST", "AVSLUTTET"])
    fun `validateRequest - deltakerListe er avsluttet - skal returnere Invalid`(status: Status): Unit = runBlocking {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(status = status),
        )

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltakerliste er avsluttet")
    }

    @Test
    fun `validateRequest - deltakerliste ikke apen for pamelding - skal returnere Invalid`(): Unit = runBlocking {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(apentForPamelding = false),
        )

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltakerliste er ikke åpen for påmelding")
    }

    @Test
    fun `validateRequest - bruker sin innsatsgruppe i deltakerliste - skal returnere Valid`(): Unit = runBlocking {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = tiltakstype.innsatsgrupper.first()))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    @Test
    fun `validateRequest - ikke ARBEIDSRETTET_REHABILITERING - skal returnere Invalid`(): Unit = runBlocking {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = null))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
    }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og ikke SITUASJONSBESTEMT_INNSATS - skal returnere Invalid`(): Unit = runBlocking {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = null))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
    }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og SITUASJONSBESTEMT_INNSATS ikke sykmeldt - skal returnere Invalid`(): Unit =
        runBlocking {
            every { deltakerListeRepository.get(any()) } returns Result.success(
                lagDeltakerListe(tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
            )

            coEvery { brukerService.get(any()) } returns
                Result.success(lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS))

            val validationResult = sut.validateRequest(requestInTest)

            validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
            validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
        }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og SITUASJONSBESTEMT_INNSATS sykmeldt - skal returnere Valid`(): Unit =
        runBlocking {
            every { deltakerListeRepository.get(any()) } returns Result.success(
                lagDeltakerListe(tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
            )

            coEvery { brukerService.get(any()) } returns
                Result.success(lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS))

            coEvery { isOppfolgingsTilfelleClient.erSykmeldtMedArbeidsgiver(any()) } returns true

            val validationResult = sut.validateRequest(requestInTest)

            validationResult.shouldBeTypeOf<ValidationResult.Valid>()
        }

    @Test
    fun `validateRequest - deltaker 18 ar - skal returnere Invalid`(): Unit = runBlocking {
        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(18).value

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltaker er for ung for å delta på GRUPPE_ARBEIDSMARKEDSOPPLAERING")
    }

    @Test
    fun `validateRequest - deltaker 18 ar - skal returnere Valid`(): Unit = runBlocking {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerListe(tiltakstype = lagTiltakstype()),
        )
        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(17).value

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    companion object {
        private val requestInTest = OpprettKladdRequest(
            deltakerlisteId = UUID.randomUUID(),
            personident = "~personident~",
        )
    }
}
