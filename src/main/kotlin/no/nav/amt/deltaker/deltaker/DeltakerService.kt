package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.OppdaterDeltakerRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.FattetAvNav
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val vedtakRepository: VedtakRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerProducer: DeltakerProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) =
        deltakerRepository.getMany(personident, deltakerlisteId)

    fun lagreKladd(
        deltaker: Deltaker,
    ) {
        deltakerRepository.upsert(deltaker)
        log.info("Lagret kladd for deltaker med id ${deltaker.id}")
    }

    suspend fun oppdaterDeltaker(oppdatertDeltaker: OppdaterDeltakerRequest) {
        val lagretDeltaker = get(oppdatertDeltaker.id).getOrThrow()

        val ansattmap = mutableMapOf<String, NavAnsatt>()
        val enhetmap = mutableMapOf<String, NavEnhet>()

        deltakerRepository.upsert(
            lagretDeltaker.copy(
                startdato = oppdatertDeltaker.startdato,
                sluttdato = oppdatertDeltaker.sluttdato,
                dagerPerUke = oppdatertDeltaker.dagerPerUke,
                deltakelsesprosent = oppdatertDeltaker.deltakelsesprosent,
                bakgrunnsinformasjon = oppdatertDeltaker.bakgrunnsinformasjon,
                innhold = oppdatertDeltaker.innhold,
                status = oppdatertDeltaker.status,
                sistEndretAv = hentOgMellomlagreNavAnsatt(ansattmap, oppdatertDeltaker.sistEndretAv).id,
                sistEndretAvEnhet = hentOgMellomlagreNavEnhet(enhetmap, oppdatertDeltaker.sistEndretAvEnhet).id,
                sistEndret = oppdatertDeltaker.sistEndret,
            ),
        )

        oppdatertDeltaker.deltakerEndring?.let {
            deltakerEndringRepository.upsert(
                DeltakerEndring(
                    id = it.id,
                    deltakerId = it.deltakerId,
                    endringstype = it.endringstype,
                    endring = it.endring,
                    endretAv = hentOgMellomlagreNavAnsatt(ansattmap, it.endretAv).id,
                    endretAvEnhet = hentOgMellomlagreNavEnhet(enhetmap, it.endretAvEnhet).id,
                    endret = it.endret,
                ),
            )
        }
        oppdatertDeltaker.vedtaksinformasjon?.let {
            vedtakRepository.upsert(
                Vedtak(
                    id = it.id,
                    deltakerId = oppdatertDeltaker.id,
                    fattet = it.fattet,
                    gyldigTil = it.gyldigTil,
                    deltakerVedVedtak = it.deltakerVedVedtak,
                    fattetAvNav = it.fattetAvNav?.let { f ->
                        FattetAvNav(
                            fattetAv = hentOgMellomlagreNavAnsatt(ansattmap, f.fattetAv).id,
                            fattetAvEnhet = hentOgMellomlagreNavEnhet(enhetmap, f.fattetAvEnhet).id,
                        )
                    },
                    opprettet = it.opprettet,
                    opprettetAv = hentOgMellomlagreNavAnsatt(ansattmap, it.opprettetAv).id,
                    opprettetAvEnhet = hentOgMellomlagreNavEnhet(enhetmap, it.opprettetAvEnhet).id,
                    sistEndret = it.sistEndret,
                    sistEndretAv = hentOgMellomlagreNavAnsatt(ansattmap, it.sistEndretAv).id,
                    sistEndretAvEnhet = hentOgMellomlagreNavEnhet(enhetmap, it.sistEndretAvEnhet).id,
                ),
            )
        }
        deltakerProducer.produce(get(oppdatertDeltaker.id).getOrThrow())
        log.info("Oppdatert deltaker med id ${oppdatertDeltaker.id}")
    }

    private suspend fun hentOgMellomlagreNavAnsatt(ansattmap: MutableMap<String, NavAnsatt>, navIdent: String): NavAnsatt {
        ansattmap[navIdent]?.let {
            return it
        }
        val ansatt = navAnsattService.hentEllerOpprettNavAnsatt(navIdent)
        ansattmap[navIdent] = ansatt
        return ansatt
    }

    private suspend fun hentOgMellomlagreNavEnhet(enhetmap: MutableMap<String, NavEnhet>, enhetsnummer: String): NavEnhet {
        enhetmap[enhetsnummer]?.let {
            return it
        }
        val enhet = navEnhetService.hentEllerOpprettNavEnhet(enhetsnummer)
        enhetmap[enhetsnummer] = enhet
        return enhet
    }
}

fun nyDeltakerStatus(type: DeltakerStatus.Type, aarsak: DeltakerStatus.Aarsak? = null) = DeltakerStatus(
    id = UUID.randomUUID(),
    type = type,
    aarsak = aarsak,
    gyldigFra = LocalDateTime.now(),
    gyldigTil = null,
    opprettet = LocalDateTime.now(),
)
