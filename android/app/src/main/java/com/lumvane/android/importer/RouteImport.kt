package com.lumvane.android.importer

import com.lumvane.android.data.Profile
import com.lumvane.android.data.Repositories

suspend fun outboundIdsByName(repos: Repositories): Map<String, Int> =
    repos.groups.getAll().flatMap { repos.profiles.getByGroup(it.id) }
        .filter { it.name.isNotBlank() }
        .groupBy { it.name }.mapValues { (_, l) -> l.first().id.toInt() }

fun shareName(p: Profile): String = p.name.ifBlank { p.displayAddress }

class RouteImport(private val repos: Repositories) {

    suspend fun decode(text: String): SharedRoute? {
        val byName = outboundIdsByName(repos)
        return RouteShare.decode(text) { byName[it] }
    }

    suspend fun save(route: SharedRoute): Long = repos.transaction {
        val id = repos.routes.createProfile(route.name.ifBlank { "Imported" }, route.defaultOutboundId)
        if (route.isRaw) {
            repos.routes.getProfile(id)?.let {
                repos.routes.updateProfile(it.copy(isRaw = true, rawRoute = route.rawRoute, preventModifications = route.preventModifications))
            }
        } else {
            repos.routes.saveRules(id, route.rules)
        }
        id
    }

    suspend fun saveRemote(routes: List<RemoteRoute>): List<Long> = repos.transaction {
        routes.map { r ->
            val id = repos.routes.createProfile(r.name)
            repos.routes.getProfile(id)?.let { repos.routes.updateProfile(it.copy(isRemote = true, remoteUrl = r.url)) }
            id
        }
    }
}
