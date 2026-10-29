package com.example.zahra.dialogue

import java.util.UUID

enum class BlockSource { TOOL, RETRIEVAL, MEMORY, CLARIFICATION, ABSTAIN, LOCAL, LEGACY }
data class ResponseBlock(val text: String, val source: BlockSource)
data class ComposedResponse(val text: String, val traceId: String, val blocks: List<ResponseBlock>)

/** The core is immutable; optional presentation cannot replace it. */
object ResponseComposer {
    fun compose(core: ResponseBlock, opening: ResponseBlock? = null, followup: ResponseBlock? = null): ComposedResponse {
        val blocks = listOfNotNull(opening, core, followup).filter { it.text.isNotBlank() }
        return ComposedResponse(blocks.joinToString("\n\n") { it.text }, UUID.randomUUID().toString(), blocks)
    }
}
