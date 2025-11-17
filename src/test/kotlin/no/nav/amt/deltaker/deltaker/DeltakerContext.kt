package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.SingletonPostgres16Container
import java.time.LocalDate

data class DeltakerContext(
    val veileder: NavAnsatt = TestData.lagNavAnsatt(),
    val navEnhet: NavEnhet = TestData.lagNavEnhet(),
    var deltaker: Deltaker = TestData.lagDeltaker(
        status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        startdato = LocalDate.now().minusMonths(1),
        sluttdato = LocalDate.now().plusMonths(3),
        deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
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

    init {
        @Suppress("UnusedExpression")
        SingletonPostgres16Container
        TestRepository.insert(veileder)
        TestRepository.insert(navEnhet)
    }

    fun withTiltakstype(tiltakskode: Tiltakskode) {
        deltaker = deltaker.copy(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = tiltakskode)),
        )
    }

    fun medVedtak(fattet: Boolean = true) {
        vedtak = vedtak.copy(fattet = if (fattet) deltaker.sistEndret.minusMonths(3) else null)
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltaker = deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    }
}
