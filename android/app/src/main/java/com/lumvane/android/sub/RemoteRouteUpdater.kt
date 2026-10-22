package com.lumvane.android.sub

import com.lumvane.android.config.RuleSetCatalog
import com.lumvane.android.data.Repositories
import com.lumvane.android.importer.RouteShare
import com.lumvane.android.importer.outboundIdsByName
import kotlinx.coroutines.CancellationException

class RemoteRouteUpdater(private val repos: Repositories, private val fetch: suspend (String) -> String) {

    data class Updated(val missingOutbounds: List<String>)

    suspend fun update(routeId: Long): Result<Updated> = try {
        val profile = repos.routes.getProfile(routeId) ?: error("routing profile $routeId not found")
        require(profile.isRemote) { "not a remote routing profile" }
        val url = profile.remoteUrl.trim()
        require(url.isNotEmpty()) { "remote URL is empty" }
        val mirror = repos.settings.get().rulesetMirror
        val body = fetch(RuleSetCatalog.mirror(url, mirror))
        val byName = outboundIdsByName(repos)
        val fetched = RouteShare.decode(body) { byName[it] } ?: error("could not parse a routing profile from the response")
        require(!fetched.isRaw) { "the remote content is a raw routing profile, which is not supported for remote profiles" }
        repos.transaction {
            val current = repos.routes.getProfile(routeId) ?: error("routing profile $routeId was deleted")
            repos.routes.saveRules(routeId, fetched.rules)
            repos.routes.updateProfile(
                current.copy(
                    isRaw = false, rawRoute = "", defaultOutboundId = fetched.defaultOutboundId,
                    name = current.name.ifBlank { fetched.name },
                    remoteLastUpdate = System.currentTimeMillis() / 1000,
                ),
            )
        }
        Result.success(Updated(fetched.missingOutbounds))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    suspend fun updateAll(onlyAuto: Boolean): Map<Long, Result<Updated>> {
        val remote = repos.routes.allProfiles().filter { it.isRemote && it.remoteUrl.isNotBlank() && (!onlyAuto || it.autoUpdate) }
        return remote.associate { it.id to update(it.id) }
    }
}
