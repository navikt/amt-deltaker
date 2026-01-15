package no.nav.amt.deltaker.deltaker.endring

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerEndringValidatorTest {
    val deltakerHistorikkServiceMock = mockk<DeltakerHistorikkService>()

    @BeforeEach
    fun setup() {
        every { deltakerHistorikkServiceMock.getForDeltaker(any()) } returns emptyList()
    }

    @Test
    fun `valider - avslutt deltakelse - ingen endring - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = deltaker.sluttdato!!,
            aarsak = null,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf(
            "AVSLUTT_DELTAKELSE_INGEN_ENDRING",
        )
    }

    @Test
    fun `valider - avslutt deltakelse - DELTAR med endret sluttdato og aarsak - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - avslutt deltakelse - HAR_SLUTTET med endret sluttdato og aarsak - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - avslutt deltakelse - ny aarsak - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = deltaker.sluttdato!!,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - avslutt deltakelse - endret aarsak - gir valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.ANNET),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = deltaker.sluttdato!!,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - avslutt deltakelse - endret sluttdato - gir valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = null,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerEndringValidator = DeltakerEndringValidator(
            deltaker = deltaker,
            deltakerHistorikkService = deltakerHistorikkServiceMock,
        )

        val validationResult = deltakerEndringValidator.validerRequest(endringsrequest)

        validationResult shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - avbryt deltakelse - AVBRUTT uten endringer - invalid`(): Unit = runBlocking {
        val sluttdato = LocalDate.now().plusDays(10)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.AVBRUTT,
                aarsak = DeltakerStatus.Aarsak.Type.ANNET,
            ),
            sluttdato = sluttdato,
        )
        val endringsrequest = AvbrytDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = sluttdato,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val res = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock).validerRequest(endringsrequest)

        res.shouldBeInstanceOf<ValidationResult.Invalid>()
        res.reasons shouldBe listOf("AVBRYT_DELTAKELSE_INGEN_ENDRING")
    }

    @Test
    fun `valider - avbryt deltakelse - DELTAR med endringer - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusDays(20),
        )
        val endringsrequest = AvbrytDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = LocalDate.now().plusDays(5),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(endringsrequest) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre avslutning - FULLFORT til ikke fullfort - valid`(): Unit = runBlocking {
        val slutt = LocalDate.now().plusDays(30)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FULLFORT),
            sluttdato = slutt,
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = null,
            harFullfort = false,
            sluttdato = slutt,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(endringsrequest) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre avslutning - ingen endring - invalid`(): Unit = runBlocking {
        val slutt = LocalDate.now().plusDays(30)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = slutt,
        )
        val request = EndreAvslutningRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = null,
            harFullfort = null,
            sluttdato = slutt,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val result = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock).validerRequest(request)

        result.shouldBeInstanceOf<ValidationResult.Invalid>()
        result.reasons shouldBe listOf("ENDRE_AVSLUTNING_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre bakgrunnsinformasjon - ingen endring - invalid`(): Unit = runBlocking {
        val tekst = "Søkes inn fordi..."
        val deltaker = TestData.lagDeltaker(bakgrunnsinformasjon = tekst)
        val request = BakgrunnsinformasjonRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            bakgrunnsinformasjon = tekst,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("ENDRE_BAKGRUNNSINFORMASJON_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre bakgrunnsinformasjon - ny tekst - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(bakgrunnsinformasjon = "gammel")
        val request = BakgrunnsinformasjonRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            bakgrunnsinformasjon = "ny",
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre innhold - ingen endring - invalid`(): Unit = runBlocking {
        val innhold = Deltakelsesinnhold("ledetekst", emptyList())
        val deltaker = TestData.lagDeltaker(innhold = innhold)
        val request = InnholdRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            deltakelsesinnhold = Deltakelsesinnhold("ledetekst", emptyList()),
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("ENDRE_INNHOLD_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre innhold - endret innhold - valid`(): Unit = runBlocking {
        val innhold = Deltakelsesinnhold("ledetekst", emptyList())
        val nyttInnhold = Deltakelsesinnhold("ny ledetekst", listOf(Innhold("test", "test", true, null)))
        val deltaker = TestData.lagDeltaker(innhold = innhold)
        val request = InnholdRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            deltakelsesinnhold = nyttInnhold,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre sluttarsak - ingen endring - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.ANNET),
        )
        val request = SluttarsakRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, null),
            forslagId = null,
            begrunnelse = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("ENDRE_SLUTTAARSAK_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre sluttarsak - ny aarsak - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET, aarsak = DeltakerStatus.Aarsak.Type.ANNET),
        )
        val request = SluttarsakRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            forslagId = null,
            begrunnelse = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre sluttdato - ingen endring - invalid`(): Unit = runBlocking {
        val sluttdato = LocalDate.now().plusDays(10)
        val deltaker = TestData.lagDeltaker(sluttdato = sluttdato)
        val request = SluttdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = sluttdato,
            forslagId = null,
            begrunnelse = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("ENDRE_SLUTTDATO_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre sluttdato - endret dato - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(sluttdato = LocalDate.now().plusDays(10))
        val request = SluttdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = LocalDate.now().plusDays(20),
            forslagId = null,
            begrunnelse = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre startdato - ingen endring - invalid`(): Unit = runBlocking {
        val start = LocalDate.now().minusDays(5)
        val slutt = LocalDate.now().plusDays(30)
        val deltaker = TestData.lagDeltaker(startdato = start, sluttdato = slutt)
        val request = StartdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            startdato = start,
            sluttdato = slutt,
            forslagId = null,
            begrunnelse = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("ENDRE_STARTDATO_INGEN_ENDRING")
    }

    @Test
    fun `valider - endre startdato - endret startdato - valid`(): Unit = runBlocking {
        val start = LocalDate.now().minusDays(5)
        val slutt = LocalDate.now().plusDays(30)
        val deltaker = TestData.lagDeltaker(startdato = start, sluttdato = slutt)
        val request = StartdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            startdato = start.plusDays(1),
            sluttdato = slutt,
            forslagId = null,
            begrunnelse = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - fjern oppstartsdato - uten startdato - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltakerKladd() // startdato = null
        val request = FjernOppstartsdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)

        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("FJERN_OPPSTARTSDATO_INGEN_ENDRING")
    }

    @Test
    fun `valider - fjern oppstartsdato - med startdato - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(startdato = LocalDate.now().minusDays(10))
        val request = FjernOppstartsdatoRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - forleng deltakelse - uten startdato - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltakerKladd() // startdato = null
        val request = ForlengDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf(
            "FORLENG_DELTAKELSE_INGEN_ENDRING",
        )
    }

    @Test
    fun `valider - forleng deltakelse - med startdato - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(startdato = LocalDate.now().minusMonths(1))
        val request = ForlengDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - sett ikke aktuell - ingen endring - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL, aarsak = DeltakerStatus.Aarsak.Type.ANNET),
        )
        val request = IkkeAktuellRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, null),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)
        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf(
            "SETT_IKKE_AKTUELL_INGEN_ENDRING",
        )
    }

    @Test
    fun `valider - sett ikke aktuell - ny aarsak - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, aarsak = null))
        val request = IkkeAktuellRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "annet"),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - reaktiver deltakelse - status ikke aktuell - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL))
        val request = ReaktiverDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - reaktiver deltakelse - status ikke IKKE_AKTUELL - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        val request = ReaktiverDeltakelseRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val validationResult = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request)

        validationResult.shouldBeInstanceOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf(
            "REAKTIVER_DELTAKELSE_INGEN_ENDRING",
        )
    }

    @Test
    fun `valider - endre deltakelsesmengde - gyldig endring - valid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val request = DeltakelsesmengdeRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            gyldigFra = LocalDate.now(),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        every { deltakerHistorikkServiceMock.getForDeltaker(any()) } returns
            listOf(
                DeltakerHistorikk.Endring(
                    TestData.lagDeltakerEndring(
                        endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                            deltakelsesprosent = 100F,
                            dagerPerUke = 5F,
                            gyldigFra = LocalDate.now().minusMonths(4),
                            begrunnelse = null,
                        ),
                    ),
                ),
            )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }

    @Test
    fun `valider - endre deltakelsesmengde - ugyldig endring - invalid`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker()
        val request = DeltakelsesmengdeRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            deltakelsesprosent = 100,
            dagerPerUke = 5,
            gyldigFra = LocalDate.now().minusMonths(4),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        every { deltakerHistorikkServiceMock.getForDeltaker(any()) } returns
            listOf(
                DeltakerHistorikk.Endring(
                    TestData.lagDeltakerEndring(
                        endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                            deltakelsesprosent = 100F,
                            dagerPerUke = 5F,
                            gyldigFra = LocalDate.now().minusMonths(4),
                            begrunnelse = null,
                        ),
                    ),
                ),
            )

        val result = DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock).validerRequest(request)
        result.shouldBeInstanceOf<ValidationResult.Invalid>()
        result.reasons shouldBe listOf("ENDRE_DELTAKELSESMENGDE_IKKE_GYLDIG_ENDRING")
    }

    @Test
    fun `valider - endre avslutning - AVBRUTT til fullfort - valid`(): Unit = runBlocking {
        val slutt = LocalDate.now().plusDays(7)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.AVBRUTT),
            sluttdato = slutt,
        )
        val request = EndreAvslutningRequest(
            endretAv = TestData.lagNavAnsatt().navIdent,
            endretAvEnhet = TestData.lagNavEnhet().enhetsnummer,
            aarsak = null,
            harFullfort = true,
            sluttdato = slutt,
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        DeltakerEndringValidator(deltaker, deltakerHistorikkServiceMock)
            .validerRequest(request) shouldBe ValidationResult.Valid
    }
}
