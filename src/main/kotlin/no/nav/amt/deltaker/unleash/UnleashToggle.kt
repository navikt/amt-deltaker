package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    private val tiltakstyperKometErMasterFor = listOf(
        Tiltakstype.ArenaKode.ARBFORB,
    )

    // her kan vi legge inn de neste tiltakstypene vi skal ta over
    private val tiltakstyperKometKanskjeErMasterFor = listOf(
        Tiltakstype.ArenaKode.INDOPPFAG,
        Tiltakstype.ArenaKode.AVKLARAG,
        Tiltakstype.ArenaKode.ARBRRHDAG,
    )

    fun erKometMasterForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean = tiltakstype in tiltakstyperKometErMasterFor ||
        (unleashClient.isEnabled("amt.enable-komet-deltakere") && tiltakstype in tiltakstyperKometKanskjeErMasterFor)

    fun skalLeseArenaDeltakereForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean =
        unleashClient.isEnabled("amt.les-arena-deltakere") && tiltakstype in tiltakstyperKometKanskjeErMasterFor
}
