package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.getForslagId
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringService(
    private val repository: DeltakerEndringRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val hendelseService: HendelseService,
    private val forslagService: ForslagService,
) {
    fun getForDeltaker(deltakerId: UUID) = repository.getForDeltaker(deltakerId)

    fun deleteForDeltaker(deltakerId: UUID) = repository.deleteForDeltaker(deltakerId)

    suspend fun upsertEndring(deltaker: Deltaker, request: EndringRequest): Result<Deltaker> {
        val (endretDeltaker, endring) = when (request) {
            is BakgrunnsinformasjonRequest -> {
                val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon(request.bakgrunnsinformasjon)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is InnholdRequest -> {
                val endring = DeltakerEndring.Endring.EndreInnhold(request.deltakelsesinnhold.ledetekst, request.deltakelsesinnhold.innhold)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is DeltakelsesmengdeRequest -> {
                val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = request.deltakelsesprosent?.toFloat(),
                    dagerPerUke = request.dagerPerUke?.toFloat(),
                    begrunnelse = request.begrunnelse,
                )
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is StartdatoRequest -> {
                val endring = DeltakerEndring.Endring.EndreStartdato(
                    startdato = request.startdato,
                    sluttdato = request.sluttdato,
                    request.begrunnelse,
                )
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is SluttdatoRequest -> {
                val endring = DeltakerEndring.Endring.EndreSluttdato(request.sluttdato, request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is SluttarsakRequest -> {
                val endring = DeltakerEndring.Endring.EndreSluttarsak(request.aarsak, request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is ForlengDeltakelseRequest -> {
                val endring = DeltakerEndring.Endring.ForlengDeltakelse(request.sluttdato, request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is IkkeAktuellRequest -> {
                val endring = DeltakerEndring.Endring.IkkeAktuell(request.aarsak, request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is AvsluttDeltakelseRequest -> {
                val endring = DeltakerEndring.Endring.AvsluttDeltakelse(request.aarsak, request.sluttdato, request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }

            is ReaktiverDeltakelseRequest -> {
                val endring = DeltakerEndring.Endring.ReaktiverDeltakelse(LocalDate.now(), request.begrunnelse)
                Pair(endretDeltaker(deltaker, endring), endring)
            }
        }

        endretDeltaker.onSuccess {
            val ansatt = navAnsattService.hentEllerOpprettNavAnsatt(request.endretAv)
            val enhet = navEnhetService.hentEllerOpprettNavEnhet(request.endretAvEnhet)
            val godkjentForslag = request.getForslagId()?.let { forslagId ->
                forslagService.godkjennForslag(
                    forslagId = forslagId,
                    godkjentAvAnsattId = ansatt.id,
                    godkjentAvEnhetId = enhet.id,
                )
            }

            val deltakerEndring = DeltakerEndring(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                endring = endring,
                endretAv = ansatt.id,
                endretAvEnhet = enhet.id,
                endret = LocalDateTime.now(),
                forslag = godkjentForslag,
            )

            repository.upsert(deltakerEndring)
            hendelseService.hendelseForDeltakerEndring(deltakerEndring, it, ansatt, enhet)
        }

        return endretDeltaker
    }

    private fun endretDeltaker(deltaker: Deltaker, endring: DeltakerEndring.Endring): Result<Deltaker> {
        fun endreDeltaker(erEndret: Boolean, block: () -> Deltaker) = if (erEndret) {
            Result.success(block())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        return when (endring) {
            is DeltakerEndring.Endring.AvsluttDeltakelse -> {
                endreDeltaker(
                    deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
                        endring.sluttdato != deltaker.sluttdato ||
                        deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak(),
                ) {
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = nyDeltakerStatus(
                            type = DeltakerStatus.Type.HAR_SLUTTET,
                            aarsak = endring.aarsak.toDeltakerStatusAarsak(),
                            gyldigFra = if (!endring.sluttdato.isBefore(LocalDate.now())) {
                                endring.sluttdato.atStartOfDay().plusDays(1)
                            } else {
                                LocalDateTime.now()
                            },
                        ),
                    )
                }
            }

            is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> {
                endreDeltaker(deltaker.bakgrunnsinformasjon != endring.bakgrunnsinformasjon) {
                    deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon)
                }
            }

            is DeltakerEndring.Endring.EndreDeltakelsesmengde -> {
                endreDeltaker(deltaker.deltakelsesprosent != endring.deltakelsesprosent || deltaker.dagerPerUke != endring.dagerPerUke) {
                    deltaker.copy(
                        deltakelsesprosent = endring.deltakelsesprosent,
                        dagerPerUke = endring.dagerPerUke,
                    )
                }
            }

            is DeltakerEndring.Endring.EndreInnhold -> {
                endreDeltaker(deltaker.deltakelsesinnhold?.innhold != endring.innhold) {
                    deltaker.copy(deltakelsesinnhold = Deltakelsesinnhold(endring.ledetekst, endring.innhold))
                }
            }

            is DeltakerEndring.Endring.EndreSluttdato -> endreDeltaker(endring.sluttdato != deltaker.sluttdato) {
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                )
            }

            is DeltakerEndring.Endring.EndreSluttarsak -> {
                endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
                    deltaker.copy(status = nyDeltakerStatus(deltaker.status.type, endring.aarsak.toDeltakerStatusAarsak()))
                }
            }

            is DeltakerEndring.Endring.EndreStartdato -> {
                endreDeltaker(deltaker.startdato != endring.startdato || deltaker.sluttdato != endring.sluttdato) {
                    val oppdatertStatus = deltaker.getStatusEndretStartOgSluttdato(
                        startdato = endring.startdato,
                        sluttdato = endring.sluttdato,
                    )
                    deltaker.copy(
                        startdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else endring.startdato,
                        sluttdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else endring.sluttdato,
                        status = oppdatertStatus,
                    )
                }
            }

            is DeltakerEndring.Endring.ForlengDeltakelse -> {
                endreDeltaker(deltaker.sluttdato != endring.sluttdato) {
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                    )
                }
            }

            is DeltakerEndring.Endring.IkkeAktuell -> {
                endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }

            is DeltakerEndring.Endring.ReaktiverDeltakelse -> {
                endreDeltaker(deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }
        }
    }

    private fun Deltaker.getStatusEndretSluttdato(sluttdato: LocalDate): DeltakerStatus =
        if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
            !sluttdato.isBefore(
                LocalDate.now(),
            )
        ) {
            nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
        } else {
            status
        }
}

fun Deltaker.getStatusEndretStartOgSluttdato(startdato: LocalDate?, sluttdato: LocalDate?): DeltakerStatus =
    if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (sluttdato != null && sluttdato.isBefore(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL)
    } else if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (startdato != null && !startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.DELTAR && (sluttdato != null && sluttdato.isBefore(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET)
    } else if (status.type == DeltakerStatus.Type.DELTAR && (startdato == null || startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato != null && !startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato == null || startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else {
        status
    }

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse,
)
