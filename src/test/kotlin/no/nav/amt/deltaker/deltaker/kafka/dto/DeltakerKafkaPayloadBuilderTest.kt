package no.nav.amt.deltaker.deltaker.kafka.dto

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerKafkaPayloadBuilderTest {
    val navAnsattRepository = mockk<NavAnsattRepository>()
    val navEnhetRepository = mockk<NavEnhetRepository>()
    val navEnhetService = mockk<NavEnhetService>()

    val deltakerHistorikkService = mockk<DeltakerHistorikkService>()
    val vurderingRepository = mockk<VurderingRepository>()
    val deltakerKafkaPayloadBuilder = DeltakerKafkaPayloadBuilder(
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        vurderingRepository = vurderingRepository,
    )
    val veileder: NavAnsatt = TestData.lagNavAnsatt()
    val navEnhet: NavEnhet = TestData.lagNavEnhet()

    var deltaker: Deltaker = TestData.lagDeltaker(
        status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        startdato = LocalDate.now().minusMonths(1),
        sluttdato = LocalDate.now().plusMonths(3),
        deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        ),
        navBruker = TestData.lagNavBruker(navVeilederId = veileder.id, navEnhetId = navEnhet.id),
    )
    var vedtak: Vedtak = TestData.lagVedtak(
        deltakerVedVedtak = deltaker,
        fattet = deltaker.sistEndret.minusMonths(3),
        opprettetAv = veileder,
        opprettetAvEnhet = navEnhet,
    )
    val historikk: MutableList<DeltakerHistorikk> = mutableListOf(DeltakerHistorikk.Vedtak(vedtak))

    @BeforeEach
    fun init() {
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk
    }

    @Test
    fun `buildDeltakerV1Record - deltaker med deltakelsesmengder - v1 har deltakelsesmengder`() {
        deltakerKafkaPayloadBuilder
            .buildDeltakerV1Record(deltaker)
            .deltakelsesmengder shouldBe historikk.toDeltakelsesmengder().map {
            DeltakerV1Dto.DeltakelsesmengdeDto(
                it.deltakelsesprosent,
                it.dagerPerUke,
                it.gyldigFra,
                it.opprettet,
            )
        }
    }

    @Test
    fun `buildDeltakerV1Record - deltaker med pa tiltak som ikke skal ha deltakelsesmengder - v1 har ikke deltakelsesmengder`(): Unit =
        Tiltakskode.entries
            .filter {
                it !in setOf(
                    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                    Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                    Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                    Tiltakskode.STUDIESPESIALISERING,
                    Tiltakskode.FAG_OG_YRKESOPPLAERING,
                    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
                )
            }.forEach {
                val deltaker2 = deltaker.copy(
                    deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = it)),
                )
                deltakerKafkaPayloadBuilder.buildDeltakerV1Record(deltaker2).deltakelsesmengder shouldBe emptyList()
            }

    @Test
    fun `buildDeltakerV1Record - deltakelsesmengde gyldig fra skal ikke vare for startdato`() {
        val nyStartdato = deltaker.startdato!!.plusMonths(1)
        val deltakerMedStartDatoFrem = deltaker
            .copy(startdato = nyStartdato)

        val endring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreStartdato(
                startdato = nyStartdato,
                sluttdato = deltaker.sluttdato,
                begrunnelse = null,
            ),
            endretAv = veileder.id,
            endretAvEnhet = navEnhet.id,
        )
        historikk.add(DeltakerHistorikk.Endring(endring))
        every { deltakerHistorikkService.getForDeltaker(deltaker.id) } returns historikk

        deltakerKafkaPayloadBuilder
            .buildDeltakerV1Record(deltakerMedStartDatoFrem)
            .deltakelsesmengder
            .first()
            .gyldigFra shouldBe nyStartdato
    }
}
