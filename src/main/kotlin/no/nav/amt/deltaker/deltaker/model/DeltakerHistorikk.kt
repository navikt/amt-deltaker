package no.nav.amt.deltaker.deltaker.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DeltakerHistorikk.Endring::class, name = "Endring"),
    JsonSubTypes.Type(value = DeltakerHistorikk.Vedtak::class, name = "Vedtak"),
)
sealed class DeltakerHistorikk {
    data class Endring(val endring: DeltakerEndring) : DeltakerHistorikk()

    data class Vedtak(val vedtak: no.nav.amt.deltaker.deltaker.model.Vedtak) : DeltakerHistorikk()
}
