package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    fun erKometMasterForTiltakstype(tiltakstype: String): Boolean {
        return unleashClient.isEnabled("amt.enable-komet-deltakere") && tiltakstype == "ARBFORB"
    }
}
