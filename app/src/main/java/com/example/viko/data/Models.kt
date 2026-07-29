package com.example.viko.data

enum class WorkerStatus { AVAILABLE, SPEAKING, LISTENING, OFFLINE }
enum class AlertStatus { ACTIVE, ACKNOWLEDGED, RESOLVED }
enum class CallState { IDLE, LISTENING, SPEAKING }

data class Worker(
    val id: String,
    val name: String,
    val nameKorean: String = "",
    val channelId: String,
    val zone: String,
    val deviceAddress: String,
    val macAddress: String,
    val firmwareVersion: String = "1.0.2",
    val batteryLevel: Int,
    val isOnline: Boolean,
    val status: WorkerStatus = WorkerStatus.AVAILABLE,
    val isPaired: Boolean = true
)

data class Channel(
    val id: String,
    val name: String,
    val description: String = "",
)

data class EmergencyAlert(
    val id: String,
    val workerId: String,
    val workerName: String,
    val zone: String,
    val team: String,
    val timeAgo: String,
    val status: AlertStatus = AlertStatus.ACTIVE
)

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

data class NearbyDevice(
    val id: String,
    val name: String,
    val address: String,
    val signalStrength: Int = -65,
    // Shown on the Setup Member sheet as "Device Status". Comes from the
    // earpiece over BLE for real; a default keeps the mock list unchanged.
    val batteryLevel: Int = 70,
    val location: String = "Central Building"
)
