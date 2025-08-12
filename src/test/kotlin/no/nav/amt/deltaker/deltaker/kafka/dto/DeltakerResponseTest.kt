package no.nav.amt.deltaker.deltaker.kafka.dto

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.tilVedtaksinformasjon
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerResponseTest {
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

data class DeltakerContext(
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
    var vedtak: Vedtak = TestData.lagVedtak(
        deltakerVedVedtak = deltaker,
        fattet = deltaker.sistEndret.minusMonths(3),
        opprettetAv = veileder,
        opprettetAvEnhet = navEnhet,
    )
    val historikk: MutableList<DeltakerHistorikk> = mutableListOf(DeltakerHistorikk.Vedtak(vedtak))
    val vurderinger = listOf(TestData.lagVurdering())

    init {
        SingletonPostgres16Container
        TestRepository.insert(veileder)
        TestRepository.insert(navEnhet)
    }

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

    fun medVedtak(fattet: Boolean = true) {
        vedtak = vedtak.copy(fattet = if (fattet) deltaker.sistEndret.minusMonths(3) else null)
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltaker = deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksinformasjon())
    }
}
