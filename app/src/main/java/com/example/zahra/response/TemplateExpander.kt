package com.example.zahra.response

import com.example.zahra.context.ConversationState
import com.example.zahra.personality.PersonalityPreferences

/**
 * Template Expander
 * 
 * Expands response templates by filling in slots with:
 * - Entity values
 * - Context information
 * - Personality-specific values
 */
class TemplateExpander {
    
    /**
     * Expand a template by filling in its slots
     * 
     * @param template The template to expand
     * @param slots Map of slot names to values
     * @param context Current conversation context
     * @param personality Current personality preferences
     * @return The expanded text
     */
    fun expandTemplate(
        template: ResponseTemplate,
        slots: Map<String, String>,
        context: ConversationState,
        personality: PersonalityPreferences
    ): String {
        var expanded = template.template
        
        // Replace entity slots
        for ((slotName, slotValue) in slots) {
            expanded = expanded.replace("\{$slotName\}", slotValue)
        }
        
        // Replace context-based slots
        expanded = expandContextSlots(expanded, context, personality)
        
        // Replace personality-based slots
        expanded = expandPersonalitySlots(expanded, personality)
        
        // Replace special slots
        expanded = expandSpecialSlots(expanded, context, personality)
        
        return expanded
    }
    
    /**
     * Extract slots for a template based on entities and context
     */
    fun extractSlotsForTemplate(
        template: ResponseTemplate,
        entities: Map<String, Any>,
        context: ConversationState
    ): Map<String, String> {
        val slots = mutableMapOf<String, String>()
        
        // Extract slots from entities
        for ((key, value) in entities) {
            slots[key] = value.toString()
        }
        
        // Extract common slots
        if (template.template.contains("{user_name}") || template.template.contains("{name}")) {
            val userName = entities["user_name"] ?: entities["name"] ?: context.turns
                .filter { it.speaker == com.example.zahra.model.Sender.USER }
                .lastOrNull()?.text?.split(" ")?.firstOrNull() ?: "there"
            slots["user_name"] = userName.toString()
            slots["name"] = userName.toString()
        }
        
        if (template.template.contains("{topic}")) {
            val topic = context.currentTopic ?: entities["topic"]?.toString() ?: "this"
            slots["topic"] = topic
        }
        
        if (template.template.contains("{previous}") || template.template.contains("{that}")) {
            val previousText = context.getPreviousTurn()?.text ?: "that"
            slots["previous"] = previousText
            slots["that"] = previousText
        }
        
        return slots
    }
    
    /**
     * Expand context-based slots
     */
    private fun expandContextSlots(
        text: String,
        context: ConversationState,
        personality: PersonalityPreferences
    ): String {
        var expanded = text
        
        // Replace {topic} with current topic
        context.currentTopic?.let { topic ->
            expanded = expanded.replace("{topic}", topic)
        }
        
        // Replace {previous} or {that} with previous turn
        context.getPreviousTurn()?.let { previousTurn ->
            expanded = expanded.replace("{previous}", previousTurn.text)
            expanded = expanded.replace("{that}", previousTurn.text)
        }
        
        // Replace {conversation_length} with turn count
        expanded = expanded.replace("{conversation_length}", context.turns.size.toString())
        
        // Replace {last_user_input} with last user input
        context.getUserTurns().lastOrNull()?.let { lastUserTurn ->
            expanded = expanded.replace("{last_user_input}", lastUserTurn.text)
        }
        
        return expanded
    }
    
    /**
     * Expand personality-based slots
     */
    private fun expandPersonalitySlots(
        text: String,
        personality: PersonalityPreferences
    ): String {
        var expanded = text
        
        // Replace {greeting} with personality-appropriate greeting
        if (text.contains("{greeting}")) {
            val greeting = com.example.zahra.personality.StyleApplier.getGreeting(personality)
            expanded = expanded.replace("{greeting}", greeting)
        }
        
        // Replace {signoff} with personality-appropriate signoff
        if (text.contains("{signoff}")) {
            val signoff = com.example.zahra.personality.StyleApplier.getFarewell(personality)
            expanded = expanded.replace("{signoff}", signoff)
        }
        
        // Replace {emoji} with personality-appropriate emoji
        if (text.contains("{emoji}")) {
            val emoji = com.example.zahra.personality.StyleApplier.getRandomEmoji(personality)
            expanded = expanded.replace("{emoji}", emoji)
        }
        
        return expanded
    }
    
    /**
     * Expand special slots
     */
    private fun expandSpecialSlots(
        text: String,
        context: ConversationState,
        personality: PersonalityPreferences
    ): String {
        var expanded = text
        
        // Replace {date} with current date
        expanded = expanded.replace("{date}", java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
        
        // Replace {time} with current time
        expanded = expanded.replace("{time}", java.text.SimpleDateFormat("HH:mm").format(java.util.Date()))
        
        // Replace {app_name} with app name
        expanded = expanded.replace("{app_name}", "Zahra")
        
        return expanded
    }
}
