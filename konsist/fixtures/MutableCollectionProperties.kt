// Fixture for the mutable-collection Konsist rule: parsed by Konsist, never compiled (it sits
// outside any source set). Classes named after a mutable form must be flagged; the rest must pass.
package com.rossomak.flashcards.konsist.fixtures

class DeclaredMutableProperty {
    val ids: MutableList<String> = mutableListOf()
}

class InferredMutableProperty {
    val ids = mutableListOf<String>()
}

class InferredCopiedProperty(source: List<String>) {
    val ids = source.toMutableList()
}

class MutableConstructorParameter(val ids: MutableSet<String>)

class ReadOnlyProperties(val names: List<String>) {
    val ids = listOf("a")
    val lookup: Map<String, Int> = emptyMap()
}

class PrivateMutableCache {
    private val cache = HashMap<String, Int>()
    private val entries: MutableMap<String, Int> = mutableMapOf()
}
