package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate

fun List<DeltakerHistorikk>.getInnsoktDatoFraImportertDeltaker(): LocalDate? = filterIsInstance<DeltakerHistorikk.ImportertFraArena>()
    .firstOrNull()
    ?.importertFraArena
    ?.deltakerVedImport
    ?.innsoktDato

fun List<DeltakerHistorikk>.getInnsoktDatoFraInnsok(): LocalDate? = filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>()
    .firstOrNull()
    ?.data
    ?.innsokt
    ?.toLocalDate()

fun List<DeltakerHistorikk>.getInnsoktDato(): LocalDate? {
    getInnsoktDatoFraImportertDeltaker()?.let { return it }
    getInnsoktDatoFraInnsok()?.let { return it }

    val vedtak = filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
    return vedtak.minByOrNull { it.opprettet }?.opprettet?.toLocalDate()
}
