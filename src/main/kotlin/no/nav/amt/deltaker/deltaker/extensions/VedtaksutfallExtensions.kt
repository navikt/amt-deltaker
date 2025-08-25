package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.deltaker.deltaker.Vedtaksutfall
import no.nav.amt.lib.models.deltaker.Vedtak

fun Vedtaksutfall.getVedtakOrThrow(msg: String = ""): Vedtak = when (this) {
    is Vedtaksutfall.OK -> vedtak
    else -> throw toException(msg)
}

private fun Vedtaksutfall.toException(detaljer: String = ""): Exception = when (this) {
    Vedtaksutfall.ManglerVedtakSomKanEndres ->
        IllegalArgumentException("Deltaker har ikke vedtak som kan endres $detaljer")

    Vedtaksutfall.VedtakAlleredeFattet ->
        IllegalArgumentException("Deltaker har allerede et fattet vedtak $detaljer")

    is Vedtaksutfall.OK ->
        IllegalStateException("Prøvde å behandle OK utfall som et exception: ${vedtak.id}")
}
