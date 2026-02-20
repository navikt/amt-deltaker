package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

sealed interface DeltakerEndringUtfall {
    class VellykketEndring(
        val deltaker: Deltaker,
        val nesteStatus: DeltakerStatus? = null,
    ) : DeltakerEndringUtfall {
        val deltakerId: String get() = deltaker.id.toString()

        override fun getOrThrow() = deltaker
    }

    class FremtidigEndring(
        val deltaker: Deltaker,
    ) : DeltakerEndringUtfall {
        override fun getOrThrow() = deltaker
    }

    class UgyldigEndring(
        val error: Throwable,
    ) : DeltakerEndringUtfall {
        override fun getOrThrow() = throw error
    }

    val erVellykket: Boolean get() = this is VellykketEndring

    val erUgyldig: Boolean get() = this is UgyldigEndring

    fun getOrThrow(): Deltaker
}
