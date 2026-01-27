package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.deltaker.deltaker.Vedtaksutfall
import no.nav.amt.lib.models.deltaker.Vedtak

fun Vedtaksutfall.getVedtakOrThrow(msg: String = ""): Vedtak = when (this) {
    is Vedtaksutfall.OK -> {
        vedtak
    }

    Vedtaksutfall.ManglerVedtakSomKanEndres -> {
        throw IllegalArgumentException("Deltaker har ikke vedtak som kan endres $msg")
    }

    Vedtaksutfall.VedtakAlleredeFattet -> {
        throw IllegalArgumentException("Deltaker har allerede et fattet vedtak $msg")
    }
}
