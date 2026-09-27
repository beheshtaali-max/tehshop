package com.example.fulluikotlin.data.local.repository

import android.util.Log
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.data.local.network.api.AuthApi
import com.example.fulluikotlin.data.local.network.model.response.ServerNetwork
import com.example.fulluikotlin.domain.model.OrganizedServers
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val userDataStore: UserDataStore,
    private val serverDataStore: ServerDataStore
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
                        device = device ?: "",
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
                    userDataStore.saveUser(user)
                    updateServersFromResponse(response.servers)

                    val devicesString = user?.devices
                    val devicesList = if (!devicesString.isNullOrEmpty()) {
                        devicesString.split("&&")
                    } else {
                        emptyList()
                    }

                    userDataStore.clearMultiLoginData()
                    userDataStore.saveMultiLoginDevices(devicesList)
                    userDataStore.saveMultiLoginCredentials(username, password, device)
                    userDataStore.saveSavedCredentials(username, password)
                    Resource.Success(user)
                }

                "AUTHENTICATE_ERROR" -> {
                    val errorMessage = when {
                        response.ERRORTEXT == "User Not Found." -> "نام کاربری یا رمز عبور اشتباه است"
                        !response.ERRORTEXT.isNullOrEmpty() -> response.ERRORTEXT
                        else -> "خطای احراز هویت"
                    }
                    Resource.Error(errorMessage)
                }

                "ERROR" -> {
                    if (response.code == "101") {
                        val devicesList = response.devices?.split("&&") ?: emptyList()
                        Resource.MultiLoginError(devicesList)
                    } else {
                        Resource.Error(response.ERRORTEXT ?: "خطای ناشناخته")
                    }
                }

                else -> {
                    Resource.Error("پاسخ نامعتبر از سرور")
                }
            }
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
                        device = device ?: "",
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
                    userDataStore.saveUser(user)

                    updateServersFromResponse(response.servers)

                    val devicesString = user?.devices
                    val devicesList = if (!devicesString.isNullOrEmpty()) {
                        devicesString.split("&&")
                    } else {
                        emptyList()
                    }

                    userDataStore.clearMultiLoginData()
                    userDataStore.saveMultiLoginDevices(devicesList)
                    userDataStore.saveMultiLoginCredentials(username, password, device)
                    Resource.Success(user)
                }

                "AUTHENTICATE_ERROR" -> {
                    Resource.Error(response.ERRORTEXT ?: "نام کاربری یا رمز عبور اشتباه است")
                }

                "ERROR" -> {
                    if (response.code == "101") {
                        val devicesList = response.devices?.split("&&") ?: emptyList()
                        Resource.MultiLoginError(devicesList)
                    } else {
                        Resource.Error(response.ERRORTEXT ?: "خطای ناشناخته")
                    }
                }

                else -> {
                    Resource.Error("پاسخ نامعتبر از سرور")
                }
            }
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
                Resource.Error(response.ERRORTEXT ?: "خطا در حذف دستگاه")
            }
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }

    private suspend fun updateServersFromResponse(serverList: List<ServerNetwork>?) {
        if (serverList.isNullOrEmpty()) {
            // AUTH/USAGE can legitimately return SUCCESS while `server` is an empty array.
            // In that case the empty array means "do not refresh servers", not "delete all servers".
            // Keep the previous cached list, organized list and selected server exactly as they are.
            withContext(Dispatchers.IO) {
                val cachedServers = serverDataStore.serversFlow.first()
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

            val cachedServers = serverDataStore.serversFlow.first()
            val hasServerListChanged = !hasSameServerPayload(cachedServers, domainServers)
            val needsOrganizedRebuild = serverDataStore.organizedServersFlow.first() == null

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
        val protocolType = try {
            ProtocolType.valueOf(network.protocol.uppercase())
        } catch (e: Exception) {
            ProtocolType.V2RAY
        }
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

        val currentSelectedServer = serverDataStore.getSelectedServer()
        val fallbackServer = servers.first()

        if (currentSelectedServer == null) {
            serverDataStore.selectServer(fallbackServer)
            serverDataStore.saveSelectedProtocol(fallbackServer.protocol)
            return
        }

        // جستجوی سرور با همان serverid در لیست جدید
        val updatedServer = servers.find { it.serverid == currentSelectedServer.serverid }

        if (updatedServer == null) {
            // سرور قبلی دیگر وجود ندارد؛ برای جلوگیری از خالی شدن Home، اولین سرور معتبر را انتخاب می‌کنیم.
            serverDataStore.selectServer(fallbackServer)
            serverDataStore.saveSelectedProtocol(fallbackServer.protocol)
        } else {
            // سرور وجود دارد، اما ممکن است اطلاعاتش تغییر کرده باشد
            // ذخیره نسخه به‌روز شده به عنوان سرور انتخاب شده
            serverDataStore.selectServer(updatedServer)
            // همچنین پروتکل آن را نیز ذخیره می‌کنیم
            serverDataStore.saveSelectedProtocol(updatedServer.protocol)
        }
    }
}