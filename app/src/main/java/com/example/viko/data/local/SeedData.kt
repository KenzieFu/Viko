package com.example.viko.data.local

import com.example.viko.domain.alert.model.EmergencyAlert
import com.example.viko.domain.channel.model.Channel
import com.example.viko.domain.message.model.TranslationMessage
import com.example.viko.domain.team.model.Worker
import com.example.viko.type.AlertStatus
import com.example.viko.type.WorkerStatus

/**
 * The demo dataset written into an empty database on first launch — four
 * channels of Vietnamese line workers as they would look on a Korean manager's
 * phone. Replaced by real `device_register` data once badges can enrol
 * themselves over the network.
 */
internal object SeedData {

    val channels = listOf(
        Channel(id = "ch1", name = "Operations", description = "Production line A"),
        Channel(id = "ch2", name = "Facilities", description = "Building management"),
        Channel(id = "ch3", name = "Maintenance", description = "Equipment repair"),
        Channel(id = "ch4", name = "Security", description = "Site security & access"),
    )

    val workers = listOf(
        // Operations — 5 members, 3 online (one currently speaking)
        worker("w1", "Nguyen Minh Tam", "응우옌 밍 땀", "ch1", "Zone 1", 1, 85, true, WorkerStatus.SPEAKING),
        worker("w2", "Le Khanh Linh", "레 칸 린", "ch1", "Zone 1", 2, 70, true),
        worker("w3", "Minh Luat Nguyen", "민 루엇 응우옌", "ch1", "Zone 3", 3, 60, true),
        worker("w4", "Pham Thi Mai", "팜 티 마이", "ch1", "Zone 1", 4, 90, false),
        worker("w5", "Do Van Hung", "도 반 훙", "ch1", "Zone 2", 5, 40, false),

        // Facilities — 5 members, none online (the greyed-out card state)
        worker("w6", "Bui Thi Hoa", "부이 티 호아", "ch2", "Zone 4", 6, 55, false),
        worker("w7", "Vu Van Khoa", "부 반 코아", "ch2", "Zone 4", 7, 80, false),
        worker("w8", "Dang Thi Lan", "당 티 란", "ch2", "Zone 4", 8, 65, false),
        worker("w9", "Ngo Van Phuc", "응오 반 푹", "ch2", "Zone 5", 9, 30, false),
        worker("w10", "Ly Thi Thu", "리 티 투", "ch2", "Zone 5", 10, 75, false),

        // Maintenance — 5 members, 3 online
        worker("w11", "Tran Van Long", "쩐 반 롱", "ch3", "Zone 2", 11, 45, true),
        worker("w12", "Hoang Van Duc", "호앙 반 득", "ch3", "Zone 2", 12, 20, true),
        worker("w13", "Phan Thi Nga", "판 티 응아", "ch3", "Zone 3", 13, 88, true),
        worker("w14", "Cao Van Son", "까오 반 손", "ch3", "Zone 3", 14, 50, false),
        worker("w15", "Truong Thi Yen", "쯔엉 티 옌", "ch3", "Zone 2", 15, 95, false),

        // Security — 5 members, 3 online
        worker("w16", "Nguyen Van Binh", "응우옌 반 빈", "ch4", "Gate A", 16, 72, true),
        worker("w17", "Le Thi Trang", "레 티 짱", "ch4", "Gate B", 17, 64, true),
        worker("w18", "Pham Van Quan", "팜 반 꽌", "ch4", "Gate A", 18, 58, true),
        worker("w19", "Dinh Thi Hue", "딘 티 후에", "ch4", "Gate C", 19, 41, false),
        worker("w20", "Vo Van Tai", "보 반 따이", "ch4", "Gate B", 20, 83, false),
    )

    val alerts = listOf(
        EmergencyAlert(
            id = "a1", workerId = "w3", workerName = "Minh Luat Nguyen",
            zone = "Zone 3", team = "Operations Team",
            timeAgo = "1m ago", status = AlertStatus.ACTIVE
        ),
        EmergencyAlert(
            id = "a2", workerId = "w11", workerName = "Tran Van Long",
            zone = "Zone 2", team = "Maintenance Team",
            timeAgo = "13:00", status = AlertStatus.RESOLVED
        ),
        EmergencyAlert(
            id = "a3", workerId = "w12", workerName = "Hoang Van Duc",
            zone = "Zone 2", team = "Maintenance Team",
            timeAgo = "10:00", status = AlertStatus.RESOLVED
        ),
    )

    val messages = listOf(
        TranslationMessage(
            id = "m1", workerId = "w1", workerName = "Nguyen Minh Tam", channelId = "ch1",
            originalText = "Vâng thủ trưởng, tôi nghĩ chúng tôi đã xử lý xong rồi. Vậy là ổn rồi ạ.",
            translatedText = "Yes boss, I think we've got them handled already now. So it's fine now.",
            timestamp = "2 min ago"
        ),
        TranslationMessage(
            id = "m2", workerId = "w11", workerName = "Tran Van Long", channelId = "ch3",
            originalText = "Máy số 3 bị hỏng, cần sửa gấp.",
            translatedText = "Machine No.3 is broken, urgent repair needed.",
            timestamp = "15 min ago"
        ),
        TranslationMessage(
            id = "m3", workerId = "w16", workerName = "Nguyen Van Binh", channelId = "ch4",
            originalText = "Cổng A đã được khóa an toàn.",
            translatedText = "Gate A is securely locked.",
            timestamp = "30 min ago"
        ),
        TranslationMessage(
            id = "m4", workerId = "w2", workerName = "Le Khanh Linh", channelId = "ch1",
            originalText = "Dây chuyền A đang hoạt động bình thường.",
            translatedText = "Line A is operating normally.",
            timestamp = "45 min ago"
        ),
    )

    private fun worker(
        id: String,
        name: String,
        nameKorean: String,
        channelId: String,
        zone: String,
        deviceNumber: Int,
        batteryLevel: Int,
        isOnline: Boolean,
        status: WorkerStatus = WorkerStatus.AVAILABLE
    ) = Worker(
        id = id,
        name = name,
        nameKorean = nameKorean,
        channelId = channelId,
        zone = zone,
        deviceAddress = "192.168.4.$deviceNumber",
        macAddress = "AA:BB:CC:DD:EE:%02X".format(deviceNumber),
        batteryLevel = batteryLevel,
        isOnline = isOnline,
        status = status
    )
}
