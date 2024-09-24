package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    fun erKometMasterForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean {
        return unleashClient.isEnabled("amt.enable-komet-deltakere") && tiltakstype == Tiltakstype.ArenaKode.ARBFORB
    }
}
