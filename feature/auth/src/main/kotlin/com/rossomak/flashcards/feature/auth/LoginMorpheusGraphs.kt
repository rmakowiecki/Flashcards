@file:Suppress("MagicNumber") // Spec values are tuning, pasted from the network-graph playground.

package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom

/** The network graph behind Login: two opposite corners, clear of the logo and the sign-in buttons. */
object LoginMorpheusGraphs {

    val graph: MorpheusGraphSpec = BrandMorpheusGraphSpecs.spec(
        Bloom(x = 0f, y = 0f, reach = 0.6f),
        Bloom(x = 1f, y = 1f, reach = 0.6f),
    )
}
