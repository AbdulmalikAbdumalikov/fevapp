package uz.selerown.fevapp.core.common

import java.util.UUID

/** Creates unique ids. An interface so tests can use predictable ids. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
