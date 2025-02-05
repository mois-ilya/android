package com.tonapps.wallet.api.internal

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.extensions.packageInfo
import com.tonapps.network.get
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import okhttp3.OkHttpClient
import org.json.JSONObject

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private fun endpoint(
        path: String,
        testnet: Boolean,
        platform: String,
        build: String
    ): String {
        val builder = Uri.Builder()
        builder.scheme("https")
            .authority("api.tonkeeper.com")
            .appendEncodedPath(path)
            .appendQueryParameter("lang", context.locale.language)
            .appendQueryParameter("build", build)
            .appendQueryParameter("platform", platform)
            .appendQueryParameter("chainName", if (testnet) "testnet" else "mainnet")
        return builder.build().toString()
    }

    private fun request(
        path: String,
        testnet: Boolean,
        platform: String = "android_x",
        build: String = "4.6.3", //context.packageInfo.versionName.removeSuffix("-debug")
    ): JSONObject {
        val url = endpoint(path, testnet, platform, build)
        val body = okHttpClient.get(url)
        return JSONObject(body)
    }

    fun getNotifications(): List<NotificationEntity> {
        val json = request("notifications", false)
        val array = json.getJSONArray("notifications")
        val list = mutableListOf<NotificationEntity>()
        for (i in 0 until array.length()) {
            list.add(NotificationEntity(array.getJSONObject(i)))
        }
        return list.toList()
    }

    fun getBrowserApps(testnet: Boolean): JSONObject {
        val data = request("apps/popular", testnet)
        return data.getJSONObject("data")
    }

    fun getFiatMethods(testnet: Boolean = false): JSONObject {
        val data = request("fiat/methods", testnet)
        return data.getJSONObject("data")
    }

    fun downloadConfig(testnet: Boolean): ConfigEntity? {
        return try {
            val json = request("keys", testnet)
            ConfigEntity(json, context.isDebug)
        } catch (e: Throwable) {
            null
        }
    }

    fun resolveCountry(): String? {
        return try {
            JSONObject(okHttpClient.get("https://api.country.is/")).getString("country")
        } catch (e: Throwable) {
            null
        }
    }

}