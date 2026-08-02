package com.example.viko.domain.message.model

data class TranslationMessage(
    val id: String,
    val workerId: String,
    val workerName: String,
    val channelId: String,
    val originalText: String,
    val translatedText: String,
    val originalLang: String = "vi",
    val targetLang: String = "ko",
    val timestamp: String
)
