package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    private val tiltakstyperKometErMasterFor = listOf(
        Tiltakstype.ArenaKode.ARBFORB,
        Tiltakstype.ArenaKode.INDOPPFAG,
        Tiltakstype.ArenaKode.AVKLARAG,
        Tiltakstype.ArenaKode.ARBRRHDAG,
        Tiltakstype.ArenaKode.DIGIOPPARB,
        Tiltakstype.ArenaKode.VASV,
    )

    private val tiltakstyperKometSkalLese = listOf(
        Tiltakstype.ArenaKode.GRUPPEAMO,
        Tiltakstype.ArenaKode.JOBBK,
        Tiltakstype.ArenaKode.GRUFAGYRKE,
    )

    fun erKometMasterForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean = tiltakstype in tiltakstyperKometErMasterFor

    fun skalLeseArenaDeltakereForTiltakstype(tiltakstype: Tiltakstype.ArenaKode): Boolean =
        unleashClient.isEnabled("amt.les-arena-deltakere") && tiltakstype in tiltakstyperKometSkalLese
}
