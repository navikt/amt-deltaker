package no.nav.amt.deltaker.deltaker.kafka.dto

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.kafka.dto.DeltakerDto
import no.nav.amt.deltaker.kafka.dto.DeltakerV1Dto
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import org.junit.Test
import java.time.LocalDate

class DeltakerDtoTest {
    @Test
    fun `DeltakerDto - deltaker med deltakelsesmengder - v1 har deltakelsesmengder`(): Unit = with(DeltakerContext()) {
        deltakerDto.v1.deltakelsesmengder shouldBe historikk.toDeltakelsesmengder().map {
            DeltakerV1Dto.DeltakelsesmengdeDto(
                it.deltakelsesprosent,
                it.dagerPerUke,
                it.gyldigFra,
                it.opprettet,
            )
        }
    }

    @Test
    fun `DeltakerDto - deltaker med på tiltak som ikke skal ha deltakelsesmengder - v1 har ikke deltakelsesmengder`(): Unit =
        with(DeltakerContext()) {
            Tiltakstype.Tiltakskode.entries
                .filter {
                    it !in setOf(
                        Tiltakstype.Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                        Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                    )
                }.forEach {
                    withTiltakstype(it)
                    deltakerDto.v1.deltakelsesmengder shouldBe emptyList()
                }
        }

    @Test
    fun `DeltakerDto - deltakelsesmengde gyldig fra skal ikke være før startdato`(): Unit = with(DeltakerContext()) {
        val nyStartdato = deltaker.startdato!!.plusMonths(1)
        withStartdato(nyStartdato)

        deltakerDto.v1.deltakelsesmengder
            .first()
            .gyldigFra shouldBe nyStartdato
    }
}

private data class DeltakerContext(
    val veileder: NavAnsatt = TestData.lagNavAnsatt(),
    val navEnhet: NavEnhet = TestData.lagNavEnhet(),
    var deltaker: Deltaker = TestData.lagDeltaker(
        status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        startdato = LocalDate.now().minusMonths(1),
        sluttdato = LocalDate.now().plusMonths(3),
        deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        ),
        navBruker = TestData.lagNavBruker(navVeilederId = veileder.id, navEnhetId = navEnhet.id),
    ),
) {
    val vedtak: Vedtak = TestData.lagVedtak(
        deltakerVedVedtak = deltaker,
        fattet = deltaker.sistEndret.minusMonths(3),
        opprettetAv = veileder,
        opprettetAvEnhet = navEnhet,
    )
    val historikk: MutableList<DeltakerHistorikk> = mutableListOf(DeltakerHistorikk.Vedtak(vedtak))
    val vurderinger = listOf(TestData.lagVurdering())
    val deltakerDto
        get() = DeltakerDto(
            deltaker,
            historikk,
            vurderinger,
            veileder,
            navEnhet,
            false,
        )

    fun withStartdato(dato: LocalDate) {
        deltaker = deltaker.copy(startdato = dato)
        val endring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreStartdato(
                startdato = dato,
                sluttdato = deltaker.sluttdato,
                begrunnelse = null,
            ),
            endretAv = veileder.id,
            endretAvEnhet = navEnhet.id,
        )
        historikk.add(DeltakerHistorikk.Endring(endring))
    }

    fun withTiltakstype(tiltakskode: Tiltakstype.Tiltakskode) {
        deltaker = deltaker.copy(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = tiltakskode)),
        )
    }
}
