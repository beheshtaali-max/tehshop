package com.example.fulluikotlin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OrganizedServers(
    val data: Map<String, Map<String, List<Server>>>
) {
    fun getProtocols(): List<String> = data.keys.toList()
    fun getGroups(protocol: String): List<String> = data[protocol]?.keys?.toList() ?: emptyList()
    fun getServers(protocol: String, group: String): List<Server> = data[protocol]?.get(group) ?: emptyList()
}