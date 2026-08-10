package depslens.core.registry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 并行查询 registry 最新版本，带内存 + 磁盘缓存 + TTL。
 * - 支持 .npmrc 作用域私有源（按包名 scope 路由到对应 registry）。
 * - offline=true 时只用缓存（无网也能看，标注可能过期）。
 */
class RegistryClient(
    private val npmrc: Npmrc? = null,
    private val offline: Boolean = false,
    private val cacheDir: File? = null,
    private val ttl: Duration = Duration.ofMinutes(10),
) {
    @Serializable
    private data class Entry(val version: String, val ts: Long)

    private val memCache = mutableMapOf<String, Entry>()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun registryFor(name: String): String {
        val scope = NpmrcParser.scopeOf(name)
        return npmrc?.registryFor(scope) ?: "https://registry.npmjs.org"
    }

    private fun diskFile(name: String): File? = cacheDir?.resolve(name.replace("/", "__") + ".json")

    private fun readDisk(name: String): Entry? {
        val f = diskFile(name) ?: return null
        if (!f.exists()) return null
        return runCatching {
            val o = json.parseToJsonElement(f.readText()).jsonObject
            Entry(o["version"]?.jsonPrimitive?.content ?: return null, o["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0)
        }.getOrNull()
    }

    private fun writeDisk(name: String, entry: Entry) {
        runCatching { diskFile(name)?.writeText(json.encodeToString(entry)) }
    }

    private fun expired(ts: Long) = System.currentTimeMillis() - ts > ttl.toMillis()

    /** 查单个包最新版本（dist-tags.latest）。 */
    suspend fun latest(name: String): String? {
        memCache[name]?.let { if (!expired(it.ts)) return it.version }
        val disk = readDisk(name)
        if (disk != null && !expired(disk.ts)) { memCache[name] = disk; return disk.version }
        if (offline) return disk?.version ?: memCache[name]?.version

        val version = withContext(Dispatchers.IO) {
            runCatching {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("${registryFor(name)}/${name.encodeURIComponent()}"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()
                val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() != 200) return@withContext null
                json.parseToJsonElement(resp.body()).jsonObject["dist-tags"]?.jsonObject?.get("latest")?.jsonPrimitive?.content
            }.getOrNull()
        } ?: return disk?.version ?: memCache[name]?.version

        val entry = Entry(version, System.currentTimeMillis())
        memCache[name] = entry
        writeDisk(name, entry)
        return version
    }

    /** 批量并行查询（协程），返回 name -> latest。 */
    suspend fun latestMany(names: List<String>): Map<String, String?> = coroutineScope {
        names.distinct().map { async { it to latest(it) } }.awaitAll().toMap()
    }

    private fun String.encodeURIComponent(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
