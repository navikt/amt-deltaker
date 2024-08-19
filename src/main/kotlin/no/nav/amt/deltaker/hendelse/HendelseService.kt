package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.hendelse.model.Hendelse
import no.nav.amt.deltaker.hendelse.model.HendelseAnsvarlig
import no.nav.amt.deltaker.hendelse.model.HendelseType
import no.nav.amt.deltaker.hendelse.model.InnholdDto
import no.nav.amt.deltaker.hendelse.model.UtkastDto
import no.nav.amt.deltaker.hendelse.model.toHendelseDeltaker
import no.nav.amt.deltaker.hendelse.model.toHendelseEndring
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class HendelseService(
    private val hendelseProducer: HendelseProducer,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val arrangorService: ArrangorService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    suspend fun hendelseForDeltakerEndring(
        deltakerEndring: DeltakerEndring,
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
    ) {
        val endring: HendelseType = if (deltakerEndring.endring is DeltakerEndring.Endring.ReaktiverDeltakelse) {
            deltakerEndring.toHendelseEndring(deltaker.toUtkastDto())
        } else {
            deltakerEndring.toHendelseEndring()
        }

        hendelseProducer.produce(nyHendelse(deltaker, navAnsatt, navEnhet, endring))
    }

    suspend fun hendelseForEndringFraArrangor(endringFraArrangor: EndringFraArrangor, deltaker: Deltaker) {
        if (deltaker.vedtaksinformasjon != null) {
            val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(deltaker.vedtaksinformasjon.sistEndretAv)
            val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(deltaker.vedtaksinformasjon.sistEndretAvEnhet)

            val endring = endringFraArrangor.toHendelseEndring()

            hendelseProducer.produce(nyHendelse(deltaker, navAnsatt, navEnhet, endring))
        } else {
            throw IllegalStateException("Kan ikke produsere hendelse for endring fra arrangør for deltaker uten vedtak, id ${deltaker.id}")
        }
    }

    suspend fun hendelseForVedtakFattetAvInnbygger(deltaker: Deltaker, vedtak: Vedtak) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(vedtak.sistEndretAv)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(vedtak.sistEndretAvEnhet)

        val endring = HendelseType.InnbyggerGodkjennUtkast(deltaker.toUtkastDto())
        hendelseProducer.produce(nyHendelse(deltaker, navAnsatt, navEnhet, endring))
    }

    suspend fun hendelseForVedtak(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        enhet: NavEnhet,
        block: (it: UtkastDto) -> HendelseType,
    ) {
        val endring = block(deltaker.toUtkastDto())
        hendelseProducer.produce(nyHendelse(deltaker, navAnsatt, enhet, endring))
    }

    private fun nyHendelse(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ): Hendelse {
        val overordnetArrangor = deltaker.deltakerliste.arrangor.overordnetArrangorId?.let { arrangorService.hentArrangor(it) }
        val forsteVedtakFattet = deltakerHistorikkService.getForsteVedtakFattet(deltaker.id)

        return Hendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            deltaker = deltaker.toHendelseDeltaker(overordnetArrangor, forsteVedtakFattet),
            ansvarlig = HendelseAnsvarlig.NavVeileder(
                id = navAnsatt.id,
                navIdent = navAnsatt.navIdent,
                navn = navAnsatt.navn,
                enhet = HendelseAnsvarlig.NavVeileder.Enhet(navEnhet.id, navEnhet.enhetsnummer),
            ),
            payload = endring,
        )
    }

    suspend fun hendelseForSistBesokt(deltaker: Deltaker, sistBesokt: ZonedDateTime) {
        val overordnetArrangor = deltaker.deltakerliste.arrangor.overordnetArrangorId?.let { arrangorService.hentArrangor(it) }
        val forsteVedtakFattet = deltakerHistorikkService.getForsteVedtakFattet(deltaker.id)

        val hendelse = Hendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            deltaker = deltaker.toHendelseDeltaker(overordnetArrangor, forsteVedtakFattet),
            ansvarlig = HendelseAnsvarlig.Deltaker(
                id = deltaker.id,
                navn = deltaker.navBruker.fulltNavn,
            ),
            payload = HendelseType.DeltakerSistBesokt(sistBesokt),
        )

        hendelseProducer.produce(hendelse)
    }
}

private fun Deltaker.toUtkastDto() = UtkastDto(
    startdato,
    sluttdato,
    dagerPerUke,
    deltakelsesprosent,
    bakgrunnsinformasjon,
    deltakelsesinnhold.innhold.toDto(),
)

private fun List<Innhold>.toDto() = this.map { InnholdDto(it.tekst, it.innholdskode, it.beskrivelse) }
