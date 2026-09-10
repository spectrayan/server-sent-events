package com.spectrayan.sse.client.models

/**
 * Represents an immutable Server-Sent Event conforming to the W3C EventSource specification.
 *
 * @property id The event identifier, used to synchronize stream positions via `Last-Event-ID`.
 * @property event The event type/name. Defaults to `"message"` if not explicitly set.
 * @property data The text payload of the event. Multiline data blocks are joined with newline (`\n`).
 * @property retry Recommended reconnection retry interval in milliseconds requested by the server.
 * @property comments Any server comment lines (lines starting with `:`) received before this event.
 */
data class ServerSentEvent(
    val id: String? = null,
    val event: String = DEFAULT_EVENT_TYPE,
    val data: String = "",
    val retry: Long? = null,
    val comments: List<String> = emptyList()
) {

    /**
     * Decodes the event [data] using the provided [parser] function (e.g., a JSON deserializer).
     */
    fun <T> decode(parser: (String) -> T): T = parser(data)

    /**
     * Returns true if this event carries an empty payload.
     */
    fun isEmpty(): Boolean = data.isEmpty()

    /**
     * Returns the lines of the payload as a list.
     */
    fun lines(): List<String> = if (data.isEmpty()) emptyList() else data.split("\n")

    companion object {
        const val DEFAULT_EVENT_TYPE = "message"
    }
}
