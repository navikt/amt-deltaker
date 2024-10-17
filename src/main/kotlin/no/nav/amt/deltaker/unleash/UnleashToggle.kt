package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    private val tiltakstyperKometAlltidErMasterFor = listOf(
        Tiltakstype.ArenaKode.ARBFORB,
    )

    // her kan vi legge inn de neste tiltakstypene vi skal ta over
    private val tiltakstyperKometKanskjeErMasterFor = emptyList<Tiltakstype.ArenaKode>()

    fun erKometMasterForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean {
        return tiltakstype in tiltakstyperKometAlltidErMasterFor ||
            (unleashClient.isEnabled("amt.enable-komet-deltakere") && tiltakstype in tiltakstyperKometKanskjeErMasterFor)
    }
}
