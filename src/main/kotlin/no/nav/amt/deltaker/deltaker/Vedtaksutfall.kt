package no.nav.amt.deltaker.deltaker

import no.nav.amt.lib.models.deltaker.Vedtak

sealed interface Vedtaksutfall {
    data class OK(
        val vedtak: Vedtak,
    ) : Vedtaksutfall

    data object ManglerVedtakSomKanEndres : Vedtaksutfall

    data object VedtakAlleredeFattet : Vedtaksutfall
}
