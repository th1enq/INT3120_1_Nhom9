package com.example.coupleapp.data

import com.example.coupleapp.data.model.EmojiCategory
import com.example.coupleapp.data.model.EmojiItem

/**
 * Helper object providing emoji data for Locket feature
 */
object EmojiHelper {
    
    /**
     * Get all available emojis
     */
    fun getEmojis(): List<EmojiItem> = defaultEmojis
    
    /**
     * Get emojis by category
     */
    fun getEmojisByCategory(category: EmojiCategory): List<EmojiItem> {
        return defaultEmojis.filter { it.category == category }
    }
    
    private val defaultEmojis = listOf(
        // Smileys
        EmojiItem("😊", "Smiling", EmojiCategory.SMILEYS),
        EmojiItem("😂", "Laughing", EmojiCategory.SMILEYS),
        EmojiItem("🥰", "Love", EmojiCategory.SMILEYS),
        EmojiItem("😘", "Kiss", EmojiCategory.SMILEYS),
        EmojiItem("😍", "Heart Eyes", EmojiCategory.SMILEYS),
        EmojiItem("🤗", "Hugging", EmojiCategory.SMILEYS),
        EmojiItem("😴", "Sleeping", EmojiCategory.SMILEYS),
        EmojiItem("🥺", "Pleading", EmojiCategory.SMILEYS),
        EmojiItem("😭", "Crying", EmojiCategory.SMILEYS),
        EmojiItem("😤", "Angry", EmojiCategory.SMILEYS),
        EmojiItem("🤔", "Thinking", EmojiCategory.SMILEYS),
        EmojiItem("😎", "Cool", EmojiCategory.SMILEYS),
        EmojiItem("🥳", "Party", EmojiCategory.SMILEYS),
        EmojiItem("😜", "Winking", EmojiCategory.SMILEYS),
        EmojiItem("🤪", "Crazy", EmojiCategory.SMILEYS),
        EmojiItem("😇", "Angel", EmojiCategory.SMILEYS),
        EmojiItem("🥶", "Cold", EmojiCategory.SMILEYS),
        EmojiItem("🤒", "Sick", EmojiCategory.SMILEYS),
        EmojiItem("😱", "Shocked", EmojiCategory.SMILEYS),
        EmojiItem("🫠", "Melting", EmojiCategory.SMILEYS),
        
        // Animals
        EmojiItem("🐱", "Cat", EmojiCategory.ANIMALS),
        EmojiItem("🐶", "Dog", EmojiCategory.ANIMALS),
        EmojiItem("🐰", "Bunny", EmojiCategory.ANIMALS),
        EmojiItem("🐻", "Bear", EmojiCategory.ANIMALS),
        EmojiItem("🦊", "Fox", EmojiCategory.ANIMALS),
        EmojiItem("🐼", "Panda", EmojiCategory.ANIMALS),
        EmojiItem("🦋", "Butterfly", EmojiCategory.ANIMALS),
        EmojiItem("🐢", "Turtle", EmojiCategory.ANIMALS),
        
        // Symbols / Hearts
        EmojiItem("❤️", "Red Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💕", "Two Hearts", EmojiCategory.SYMBOLS),
        EmojiItem("💖", "Sparkling Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💗", "Growing Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💘", "Heart Arrow", EmojiCategory.SYMBOLS),
        EmojiItem("💝", "Heart Gift", EmojiCategory.SYMBOLS),
        EmojiItem("🌸", "Cherry Blossom", EmojiCategory.SYMBOLS),
        EmojiItem("✨", "Sparkles", EmojiCategory.SYMBOLS)
    )
}
