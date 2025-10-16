package no.nav.amt.deltaker.unleash

import io.getunleash.Unleash
import no.nav.amt.lib.models.deltakerliste.tiltakstype.ArenaKode

class UnleashToggle(
    private val unleashClient: Unleash,
) {
    fun erKometMasterForTiltakstype(tiltakstype: ArenaKode): Boolean = tiltakstype in tiltakstyperKometErMasterFor ||
        (unleashClient.isEnabled(ENABLE_KOMET_DELTAKERE) && tiltakstype in tiltakstyperKometKanskjeErMasterFor)

    fun skalLeseArenaDeltakereForTiltakstype(tiltakstype: ArenaKode): Boolean =
        unleashClient.isEnabled(LES_ARENA_DELTAKERE) && tiltakstype in tiltakstyperKometKanLese

    fun skalLeseGjennomforingerV2(): Boolean = unleashClient.isEnabled(LES_GJENNOMFORINGER_V2)

    companion object {
        private const val ENABLE_KOMET_DELTAKERE = "amt.enable-komet-deltakere"
        private const val LES_ARENA_DELTAKERE = "amt.les-arena-deltakere"
        private const val LES_GJENNOMFORINGER_V2 = "amt.les-gjennomforing-v2"

        private val tiltakstyperKometErMasterFor = setOf(
            ArenaKode.ARBFORB,
            ArenaKode.INDOPPFAG,
            ArenaKode.AVKLARAG,
            ArenaKode.ARBRRHDAG,
            ArenaKode.DIGIOPPARB,
            ArenaKode.VASV,
            ArenaKode.GRUPPEAMO,
            ArenaKode.JOBBK,
            ArenaKode.GRUFAGYRKE,
        )

        private val tiltakstyperKometKanLese = setOf(
            ArenaKode.ENKELAMO,
            ArenaKode.ENKFAGYRKE,
            ArenaKode.HOYEREUTD,
        )

        private val tiltakstyperKometKanskjeErMasterFor = tiltakstyperKometKanLese
    }
}
