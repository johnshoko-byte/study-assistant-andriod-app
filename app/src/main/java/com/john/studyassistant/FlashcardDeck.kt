package com.john.studyassistant

data class FlashcardDeck(
    var id: String? = "",
    var title: String? = "",
    var ownerId: String? = "",
    var cardCount: Int = 0,
    var isPublic: Boolean = false
)