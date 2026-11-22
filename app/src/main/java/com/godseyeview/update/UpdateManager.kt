package com.godseyeview.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Update channels (plan Q23): Stable sees full releases only, Beta also
 *  sees prereleases. */
enum class UpdateChannel { STABLE, BETA }

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val sha256Url: String?,
    val sizeBytes: Long,
)

/** Semver-ish comparison for names like 1.0.0, 1.0.0-beta.23, 1.0.1:
 *  numeric core first; a release outranks its prereleases; prerelease
 *  labels compare alphabetically, then numerically (beta.23 > beta.9). */
object Versions {
    fun compare(a: String, b: String): Int {
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val diff = coreA.getOrElse(i) { 0 } - coreB.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        if (preA == null && preB == null) return 0
        if (preA == null) return 1
        if (preB == null) return -1
        val labelDiff = preA.first.compareTo(preB.first)
        if (labelDiff != 0) return labelDiff
        return preA.second - preB.second
    }

    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    private fun split(version: String): Pair<List<Int>, Pair<String, Int>?> {
        val corePart = version.substringBefore('-')
        val core = corePart.split('.').map { it.toIntOrNull() ?: 0 }
        val prePart = version.substringAfter('-', "")
        if (prePart.isEmpty()) return core to null
        val label = prePart.substringBefore('.')
        val number = prePart.substringAfter('.', "").toIntOrNull() ?: 0
        return core to (label to number)
    }
}

/** Plan Q23 updater, Pool Helper style: the app checks GitHub Releases
 *  itself (public repo, no token), downloads the APK with its own
 *  downloader, verifies the SHA-256 against the release's published
 *  .sha256 asset, and hands the verified file to the system installer
 *  (one user tap - Android blocks silent installs for normal apps). */
class UpdateManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    /** The channel a build belongs to until the user picks one: beta
     *  builds track Beta, stable builds track Stable. */
    fun defaultChannel(): UpdateChannel =
        if (currentVersionName().contains("-beta")) UpdateChannel.BETA else UpdateChannel.STABLE

    fun check(
        channel: UpdateChannel,
        onResult: (UpdateInfo?) -> Unit,
        onError: (String?) -> Unit,
    ) {
        executor.execute {
            try {
                onResult(findUpdate(channel))
            } catch (e: Exception) {
                onError(e.message)
            }
        }
    }

    private fun findUpdate(channel: UpdateChannel): UpdateInfo? {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(body)
        var best: UpdateInfo? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            if (channel == UpdateChannel.STABLE && release.optBoolean("prerelease")) continue
            val version = release.getString("tag_name").removePrefix("v")
            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            var shaUrl: String? = null
            var size = 0L
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val name = asset.getString("name")
                when {
                    name.endsWith(".apk") -> {
                        apkUrl = asset.getString("browser_download_url")
                        size = asset.optLong("size")
                    }
                    name.endsWith(".apk.sha256") ->
                        shaUrl = asset.getString("browser_download_url")
                }
            }
            val url = apkUrl ?: continue
            val candidate = UpdateInfo(version, url, shaUrl, size)
            if (best == null || Versions.compare(candidate.version, best.version) > 0) {
                best = candidate
            }
        }
        val found = best ?: return null
        return if (Versions.isNewer(found.version, currentVersionName())) found else null
    }

    /** Download + verify on the executor; onDone gets the verified file. */
    fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
        onDone: (File) -> Unit,
        onError: (String?) -> Unit,
    ) {
        executor.execute {
            try {
                val expectedHash = info.sha256Url?.let { url ->
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.inputStream.bufferedReader().use { it.readText() }
                        .trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
                }
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "GodsEyeView-${info.version}.apk")
                val digest = MessageDigest.getInstance("SHA-256")
                val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            readTotal += read
                            if (info.sizeBytes > 0) {
                                onProgress((readTotal * 100 / info.sizeBytes).toInt())
                            }
                        }
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (expectedHash != null && actualHash != expectedHash) {
                    file.delete()
                    onError("checksum")
                    return@execute
                }
                onDone(file)
            } catch (e: Exception) {
                onError(e.message)
            }
        }
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/brandonmathew/GodsEyeView-Android/releases?per_page=30"
    }
}
