package com.example.viko.view.screens.channel

import com.example.viko.domain.channel.model.Channel

/**
 * What a channel list shows for [query]: the channels that match it, and — when
 * the typed name does not exist yet — that name, so the list can offer to
 * create it. Shared by the picker sheet and Manage Channel so both agree on what
 * "a miss" is.
 */
data class ChannelSearchResult(
    val matches: List<Channel>,
    val nameToCreate: String?
)

fun searchChannels(channels: List<Channel>, query: String): ChannelSearchResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return ChannelSearchResult(channels, nameToCreate = null)
    val matches = channels.filter { it.name.contains(trimmed, ignoreCase = true) }
    val exists = channels.any { it.name.equals(trimmed, ignoreCase = true) }
    return ChannelSearchResult(matches, nameToCreate = trimmed.takeUnless { exists })
}
