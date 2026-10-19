package com.shokirjon.cartubedownloader.ui

/** The IFrame API reports these documented codes; keep a useful fallback for unknown failures. */
internal object EmbedErrors {
    fun message(code: Int?): String = when (code) {
        2 -> "Video havolasi noto‘g‘ri."
        5 -> "Bu video ichki playerda ijro etilmadi."
        100 -> "Video o‘chirilgan yoki yopiq."
        101, 150 -> "Muallif bu videoni ichki oynada ko‘rishga ruxsat bermagan."
        153 -> "YouTube ilovani aniqlay olmadi (153). Diagnostika logini yuboring."
        else -> "Bu videoni ichki oynada ko‘rib bo‘lmadi."
    }
}
