package com.example.fulluikotlin.data.local.repository

import android.util.Log
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.data.local.network.api.AuthApi
import com.example.fulluikotlin.data.local.network.model.response.ServerNetwork
import com.example.fulluikotlin.domain.model.OrganizedServers
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.reporting.FirebaseErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val userDataStore: UserDataStore,
    private val serverDataStore: ServerDataStore,
    private val appDetailsDataStore: AppDetailsDataStore
) : AuthRepository {

    override suspend fun login(
        username: String,
        password: String,
        os: String,
        device: String
    ): Resource<User> {
        return try {
            val response = authApi.login(username, password, os, device)
            when (response.status) {
                "SUCCESS" -> {
                    val user = User(
                        id = response.user_id ?: "",
                        username = username,
                        device = device,
                        devices = response.devices,
                        creationDate = response.creation_date ?: "",
                        expDate = response.exp_date ?: "",
                        totalTraffic = response.total_traffic ?: "0",
                        remainTraffic = response.remain_traffic ?: "0",
                        groupName = response.group_name ?: "",
                        remainDays = response.remain_days?.coerceAtLeast(0f) ?: 0f,
                        remainDevice = response.remain_device ?: "",
                        isExpired = response.isExpired ?: true
                    )
                    // AUTH succeeded. A server-cache problem is reported separately and must not
                    // turn a valid login into an authentication failure.
                    updateServersWithoutBreakingAuthentication(response.servers)
                    userDataStore.saveAuthenticatedSession(user, password)
                    Resource.Success(user)
                }

                "AUTHENTICATE_ERROR" -> {
                    val errorCode = if (isInvalidCredentialMessage(response.ERRORTEXT)) {
                        AppErrorCode.AUTH_INVALID_CREDENTIALS
                    } else {
                        AppErrorCode.AUTH_ACCOUNT_REJECTED
                    }

                    val supportCode = buildServerSupportCode(
                        errorCode = errorCode,
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    reportServerAuthErrorIfNeeded(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "manual_or_auto_login_authenticate_error",
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )

                    Resource.Error(
                        message = errorCode.displayMessage(supportCode),
                        supportCode = supportCode,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                }

                "ERROR" -> {
                    if (response.code == "101") {
                        val devicesList = response.devices?.split("&&") ?: emptyList()
                        Resource.MultiLoginError(devicesList)
                    } else {
                        val errorCode = AppErrorCode.AUTH_ACCOUNT_REJECTED
                        val supportCode = buildServerSupportCode(
                            errorCode = errorCode,
                            status = response.status,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                        reportServerAuthErrorIfNeeded(
                            errorCode = errorCode,
                            supportCode = supportCode,
                            context = "manual_or_auto_login_server_error",
                            status = response.status,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                        Resource.Error(
                            message = errorCode.displayMessage(supportCode),
                            supportCode = supportCode,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                    }
                }

                else -> {
                    val errorCode = AppErrorCode.AUTH_INVALID_RESPONSE
                    val supportCode = buildServerSupportCode(
                        errorCode = errorCode,
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    reportServerAuthErrorIfNeeded(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "manual_or_auto_login_unknown_status",
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    Resource.Error(
                        message = errorCode.displayMessage(supportCode),
                        supportCode = supportCode,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }

    override suspend fun usage(
        username: String,
        password: String,
        usage: String,
        device: String,
        os: String
    ): Resource<User> {
        return try {
            val response = authApi.usage(username, password, usage, device, os)
            when (response.status) {
                "SUCCESS" -> {
                    val user = User(
                        id = response.user_id ?: "",
                        username = username,
                        device = device,
                        devices = response.devices,
                        creationDate = response.creation_date ?: "",
                        expDate = response.exp_date ?: "",
                        totalTraffic = response.total_traffic ?: "0",
                        remainTraffic = response.remain_traffic ?: "0",
                        groupName = response.group_name ?: "",
                        remainDays = response.remain_days?.coerceAtLeast(0f) ?: 0f,
                        remainDevice = response.remain_device ?: "",
                        isExpired = response.isExpired ?: true
                    )
                    updateServersWithoutBreakingAuthentication(response.servers)
                    userDataStore.saveAuthenticatedSession(user, password)
                    Resource.Success(user)
                }

                "AUTHENTICATE_ERROR" -> {
                    val errorCode = if (isInvalidCredentialMessage(response.ERRORTEXT)) {
                        AppErrorCode.AUTH_INVALID_CREDENTIALS
                    } else {
                        AppErrorCode.AUTH_ACCOUNT_REJECTED
                    }
                    val supportCode = buildServerSupportCode(
                        errorCode = errorCode,
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    reportServerAuthErrorIfNeeded(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "usage_authenticate_error",
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    Resource.Error(
                        message = errorCode.displayMessage(supportCode),
                        supportCode = supportCode,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                }

                "ERROR" -> {
                    if (response.code == "101") {
                        val devicesList = response.devices?.split("&&") ?: emptyList()
                        Resource.MultiLoginError(devicesList)
                    } else {
                        val errorCode = AppErrorCode.AUTH_ACCOUNT_REJECTED
                        val supportCode = buildServerSupportCode(
                            errorCode = errorCode,
                            status = response.status,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                        reportServerAuthErrorIfNeeded(
                            errorCode = errorCode,
                            supportCode = supportCode,
                            context = "usage_server_error",
                            status = response.status,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                        Resource.Error(
                            message = errorCode.displayMessage(supportCode),
                            supportCode = supportCode,
                            serverCode = response.code,
                            serverMessage = response.ERRORTEXT
                        )
                    }
                }

                else -> {
                    val errorCode = AppErrorCode.AUTH_INVALID_RESPONSE
                    val supportCode = buildServerSupportCode(
                        errorCode = errorCode,
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    reportServerAuthErrorIfNeeded(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "usage_unknown_status",
                        status = response.status,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                    Resource.Error(
                        message = errorCode.displayMessage(supportCode),
                        supportCode = supportCode,
                        serverCode = response.code,
                        serverMessage = response.ERRORTEXT
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }

    override suspend fun deleteDevice(username: String, device: String): Resource<Unit> {
        return try {
            val response = authApi.deleteDevice(username, device)
            if (response.STATUS == "SUCCESS") {
                Resource.Success(Unit)
            } else {
                val errorCode = AppErrorCode.DEVICE_DELETE_FAILED
                val supportCode = buildServerSupportCode(
                    errorCode = errorCode,
                    status = response.STATUS,
                    serverCode = null,
                    serverMessage = response.ERRORTEXT
                )
                reportServerAuthErrorIfNeeded(
                    errorCode = errorCode,
                    supportCode = supportCode,
                    context = "delete_device_server_error",
                    status = response.STATUS,
                    serverCode = null,
                    serverMessage = response.ERRORTEXT
                )
                Resource.Error(
                    message = errorCode.displayMessage(supportCode),
                    supportCode = supportCode,
                    serverMessage = response.ERRORTEXT
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }

    private fun isInvalidCredentialMessage(serverMessage: String?): Boolean {
        val normalized = serverMessage.orEmpty().trim().lowercase()
        return normalized.isBlank() ||
            normalized.contains("user not found") ||
            normalized.contains("invalid credential") ||
            normalized.contains("invalid password") ||
            normalized.contains("wrong password") ||
            normalized.contains("incorrect password")
    }

    private fun buildServerSupportCode(
        errorCode: AppErrorCode,
        status: String?,
        serverCode: String?,
        serverMessage: String?
    ): String {
        if (errorCode == AppErrorCode.AUTH_INVALID_CREDENTIALS) {
            return errorCode.code
        }

        val fingerprintInput = listOf(status, serverCode, serverMessage)
            .joinToString("|") { it.orEmpty().trim().lowercase() }
        val suffix = Integer.toUnsignedString(fingerprintInput.hashCode(), 36)
            .uppercase()
            .padStart(4, '0')
            .takeLast(4)
        return "${errorCode.code}-$suffix"
    }

    private fun reportServerAuthErrorIfNeeded(
        errorCode: AppErrorCode,
        supportCode: String,
        context: String,
        status: String?,
        serverCode: String?,
        serverMessage: String?
    ) {
        FirebaseErrorReporter.report(
            errorCode = errorCode,
            supportCode = supportCode,
            context = context,
            details = mapOf(
                "server_status" to status,
                "server_code" to serverCode,
                "server_message" to serverMessage
            )
        )
    }

    private suspend fun updateServersWithoutBreakingAuthentication(serverList: List<ServerNetwork>?) {
        try {
            updateServersFromResponse(serverList)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // AUTH succeeded. A damaged/temporarily unwritable server cache must not be
            // reported to Splash as a failed login or force the user into an auth loop.
            Log.e("AuthRepository", "server cache update failed after successful authentication", error)
            FirebaseErrorReporter.report(
                errorCode = AppErrorCode.LOCAL_STORAGE_FAILED,
                context = "server_cache_after_auth",
                throwable = error
            )
        }
    }

    private suspend fun updateServersFromResponse(serverList: List<ServerNetwork>?) {
        if (serverList.isNullOrEmpty()) {
            // AUTH/USAGE can legitimately return SUCCESS while `server` is an empty array.
            // In that case the empty array means "do not refresh servers", not "delete all servers".
            // Keep the previous cached list, organized list and selected server exactly as they are.
            withContext(Dispatchers.IO) {
                val cachedServers = serverDataStore.getServers()
                if (cachedServers.isEmpty()) {
                    Log.w(
                        "AuthRepository",
                        "server list update ignored: API returned empty/null list and no cached servers exist"
                    )
                } else {
                    Log.w(
                        "AuthRepository",
                        "server list update ignored: API returned empty/null list; using cached servers count=${cachedServers.size}"
                    )
                    validateAndFixSelectedServer(cachedServers)
                }
            }
            return
        }

        withContext(Dispatchers.IO) {
            val currentSelectedServer = serverDataStore.getSelectedServer()
            val currentSelectedId = currentSelectedServer?.serverid

            val domainServers = serverList.mapIndexed { index, network ->
                mapToDomain(
                    network = network,
                    isActive = currentSelectedId?.let { it == network.serverid } ?: (index == 0)
                )
            }

            val cachedServers = serverDataStore.getServers()
            val hasServerListChanged = !hasSameServerPayload(cachedServers, domainServers)
            val needsOrganizedRebuild = serverDataStore.getOrganizedServers() == null

            if (hasServerListChanged || needsOrganizedRebuild) {
                if (hasServerListChanged) {
                    serverDataStore.saveServers(domainServers)
                }

                val organized = domainServers.groupBy { it.protocol.name }
                    .mapValues { (_, serversByProtocol) ->
                        serversByProtocol.groupBy { it.serverimagename.ifBlank { "unknown" } }
                    }
                val organizedServers = OrganizedServers(organized)
                serverDataStore.saveOrganizedServers(organizedServers)
            } else {
                Log.d("AuthRepository", "server list update skipped: payload unchanged")
            }

            validateAndFixSelectedServer(domainServers)
        }
    }

    private fun hasSameServerPayload(oldServers: List<Server>, newServers: List<Server>): Boolean {
        if (oldServers.size != newServers.size) return false

        return oldServers.zip(newServers).all { (old, new) ->
            old.serverid == new.serverid &&
                    old.servername == new.servername &&
                    old.servergroup == new.servergroup &&
                    old.serverimagename == new.serverimagename &&
                    old.serversignal == new.serversignal &&
                    old.serverusername == new.serverusername &&
                    old.serverpassword == new.serverpassword &&
                    old.config == new.config &&
                    old.extra == new.extra &&
                    old.protocol == new.protocol
        }
    }

    private fun mapToDomain(network: ServerNetwork, isActive: Boolean): Server {
        val protocolType = ProtocolType.fromExternalName(network.protocol)
            ?: ProtocolType.V2RAY
        return Server(
            serverid = network.serverid,
            servername = network.servername,
            servergroup = network.servergroup,
            serverimagename = network.serverimagename,
            serversignal = network.serversignal,
            serverusername = network.serverusername,
            serverpassword = network.serverpassword,
            config = network.config,
            extra = network.extra,
            protocol = protocolType,
            ip = "",
            country = "",
            ping = "",
            isActive = isActive
        )
    }

    // AuthRepositoryImpl.kt

    private suspend fun validateAndFixSelectedServer(servers: List<Server>) {
        if (servers.isEmpty()) {
            serverDataStore.clearSelectedServerAndProtocol()
            return
        }

        val availableProtocols = servers.map { it.protocol }.distinct()
        val savedProtocol = serverDataStore.getSelectedProtocol()
            ?.takeIf { it in availableProtocols }
        val apiDefaultProtocol = ProtocolType.fromExternalName(
            appDetailsDataStore.getDefaultProtocol()
        )?.takeIf { it in availableProtocols }

        // A valid user choice always wins. GETAPPDETAILS.defaultprotocol is only the
        // first-use/fallback choice when there is no valid selection in DataStore.
        val preferredProtocol = savedProtocol ?: apiDefaultProtocol ?: servers.first().protocol

        if (serverDataStore.isSmartServerSelection()) {
            serverDataStore.selectSmartServer(preferredProtocol)
            return
        }

        val currentSelectedServer = serverDataStore.getSelectedServer()
        val refreshedCurrentServer = currentSelectedServer?.let { current ->
            servers.firstOrNull {
                it.serverid == current.serverid && it.protocol == preferredProtocol
            }
        }

        val selected = refreshedCurrentServer
            ?: servers.firstOrNull { it.protocol == preferredProtocol }
            ?: servers.first()

        serverDataStore.selectServer(selected)
    }

}