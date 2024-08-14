package no.nav.amt.deltaker.deltaker.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class DeltakerHistorikk {
    data class Endring(
        val endring: DeltakerEndring,
    ) : DeltakerHistorikk()

    data class Vedtak(
        val vedtak: no.nav.amt.deltaker.deltaker.model.Vedtak,
    ) : DeltakerHistorikk()

    data class Forslag(
        val forslag: no.nav.amt.lib.models.arrangor.melding.Forslag,
    ) : DeltakerHistorikk()

    data class EndringFraArrangor(
        val endringFraArrangor: no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor,
    ) : DeltakerHistorikk()
}

fun Forslag.skalInkluderesIHistorikk() = when (this.status) {
    is Forslag.Status.Avvist,
    is Forslag.Status.Erstattet,
    is Forslag.Status.Tilbakekalt,
    -> true

    is Forslag.Status.Godkjent,
    Forslag.Status.VenterPaSvar,
    -> false
}
