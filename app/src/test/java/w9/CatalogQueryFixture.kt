package w9

import kotlin.coroutines.Continuation

/** Bytecode-derived 1606 query shape, including an unrelated old preferred-name method. */
@Suppress("UNUSED_PARAMETER")
class Q {
    fun F(path: String, parameters: Map<String, String>, continuation: Continuation<Any>): Any =
        "catalog-1606:$path:${parameters["l"]}"

    fun B(continuation: Continuation<Any>): Any = "other-request"
}
