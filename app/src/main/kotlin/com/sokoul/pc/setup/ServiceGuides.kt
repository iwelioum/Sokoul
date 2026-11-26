package com.sokoul.pc.setup

import com.sokoul.credentials.GuideText
import com.sokoul.credentials.ServiceGuides as SharedGuides
import com.sokoul.pc.data.L

data class ServiceGuide(
    val id: String,
    val name: String,
    val adds: L,
    val cost: L,
    val steps: List<L>,
    val link: String?,
    val linkLabel: L?,
    val privacy: L,
    val optional: Boolean = true,
    val keyFormat: Regex? = null,
    val verified: String,
)

object ServiceGuides {
    val all = SharedGuides.all.map { guide ->
        ServiceGuide(
            guide.id, guide.name, guide.adds.local(), guide.cost.local(), guide.steps.map { it.local() },
            guide.link, guide.linkLabel?.local(), guide.privacy.local(), guide.optional, guide.keyFormat, guide.verified,
        )
    }
    private val byId = all.associateBy { it.id }

    fun of(id: String): ServiceGuide? = SharedGuides.of(id)?.let { byId[it.id] }

    private fun GuideText.local() = L(en, fr)
}
