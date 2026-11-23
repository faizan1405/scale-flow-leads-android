package com.scaleflow.leads.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

data class AvailableUpdate(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String
)

class AppUpdater(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun currentVersionName(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun checkForUpdate(githubRepo: String): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = githubRepo.trim().removePrefix("https://github.com/").trim('/')
            if (cleanRepo.isBlank() || !cleanRepo.contains("/")) return@runCatching null

            val apiUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ScaleFlowLeads-Android")
            }
            try {
                val code = conn.responseCode
                if (code == 404) return@runCatching null // No releases published yet
                if (code !in 200..299) error("GitHub API HTTP $code")

                val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(text)
                val latestVer = release.tagName.trim().removePrefix("v").removePrefix("V")
                val currentVer = currentVersionName().trim().removePrefix("v").removePrefix("V")

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@runCatching null

                if (isNewerVersion(latestVer, currentVer)) {
                    AvailableUpdate(
                        version = latestVer,
                        title = release.name.ifBlank { "Version $latestVer" },
                        notes = release.body.trim(),
                        apkUrl = apkAsset.downloadUrl
                    )
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun downloadAndInstall(
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "ScaleFlowLeads-update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "ScaleFlowLeads-Android")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) error("Download failed: HTTP $code")
                val totalBytes = conn.contentLengthLong
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            withContext(Dispatchers.Main) {
                launchInstaller(apkFile)
            }
        }
    }

    private fun launchInstaller(apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        fun isNewerVersion(latest: String, current: String): Boolean {
            val lParts = latest.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val cParts = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val maxLen = maxOf(lParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val l = lParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }
}
