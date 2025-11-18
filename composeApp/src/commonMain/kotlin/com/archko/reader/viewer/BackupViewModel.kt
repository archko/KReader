package com.archko.reader.viewer

import androidx.lifecycle.ViewModel
import com.archko.reader.pdf.cache.getWebdavCacheDir
import com.archko.reader.pdf.cache.saveWebdavCacheFile
import io.github.triangleofice.dav4kmp.DavCollection
import io.github.triangleofice.dav4kmp.DavResource
import io.github.triangleofice.dav4kmp.Response.HrefRelation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.io.File

/**
 * @author: archko 2020/11/16 :11:23
 */
class BackupViewModel : ViewModel() {

    //private val progressDao by lazy { Graph.database.progressDao() }
    var webdavUser: WebdavUser? = null
    private var httpClient: HttpClient? = null
    private var davCollection: DavCollection? = null

    data class DavResourceItem(
        val resource: DavResource,
        val isDirectory: Boolean
    )

    private val _uiDavResourceModel = MutableStateFlow<List<DavResourceItem>?>(null)
    val uiDavResourceModel: StateFlow<List<DavResourceItem>?>
        get() = _uiDavResourceModel.asStateFlow()

    //private val _uiRestoreModel = MutableStateFlow<ResponseHandler<Boolean>?>(null)
    //val uiRestoreModel: StateFlow<ResponseHandler<Boolean>?>
    //    get() = _uiRestoreModel.asStateFlow()

    fun checkAndLoadUser(): Boolean {
        if (null == webdavUser) {
            try {
                val dir = getWebdavCacheDir()
                val file = File(dir, KEY_CONFIG_USER)
                if (!file.exists()) {
                    return false
                }
                val content = file.readText()
                if (content.isEmpty()) {
                    return false
                }
                println(content)

                val user = Json.decodeFromString<WebdavUser>(content)
                if (user.name.isEmpty() || user.pass.isEmpty()
                    || user.host.isEmpty() || user.path.isEmpty()
                ) {
                    return false
                }

                webdavUser = user
                httpClient = createHttpClient(user.name, user.pass)
                davCollection =
                    DavCollection(httpClient!!, Url(buildWebdavUrl(user.host, user.path)))
                return true
            } catch (e: Exception) {
                println("checkAndLoadUser error: ${e.message}")
                return false
            }
        }
        return true
    }

    /*fun backupFiles() = flow {
        try {
            var files: Array<File>? = null
            val dir = getWebdavCacheFile("webdav")
            if (dir.exists()) {
                files = dir.listFiles { pathname: File -> pathname.name.startsWith("mupdf_") }
                if (files != null) {
                    Arrays.sort(files) { f1: File?, f2: File? ->
                        if (f1 == null) throw RuntimeException("f1 is null inside sort")
                        if (f2 == null) throw RuntimeException("f2 is null inside sort")
                        return@sort f2.lastModified().compareTo(f1.lastModified())
                    }
                }
            }
            val list = ArrayList<File>()
            if (files != null) {
                for (f in files) {
                    list.add(f)
                }
            }

            //emit(ResponseHandler.Success(list))
        } catch (e: Exception) {
            //emit(ResponseHandler.Failure())
        }
    }*/

    fun backupToWebdav() = flow {
        try {
            if (!checkAndLoadUser() || davCollection == null) {
                emit(false)
                return@flow
            }
            /*val root = JSONObject()
            val list = progressDao.getAllProgress()
            val ja = JSONArray()
            root.put("root", ja)
            list?.run {
                for (progress in list) {
                    BookProgressParser.addProgressToJson(progress, ja)
                }
            }
            val content = root.toString()
            println("backupToWebdav.content:$content")

            // 使用 dav4kmp 上传文件
            val fileName = DEFAULT_JSON
            val fileUrl = buildWebdavUrl(webdavUser!!.host, "${webdavUser!!.path}/$fileName")
            
            davCollection!!.put(
                body = content.toByteArray()
            ) { response ->
                // 上传成功回调
                println("Upload successful: ${response.status}")
            }*/
            emit(true)
        } catch (e: Exception) {
            emit(false)
            println(e.message)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun restoreFromWebdav(name: String) = flow {
        try {
            if (!checkAndLoadUser() || davCollection == null) {
                emit(false)
                return@flow
            }

            // 使用 dav4kmp 下载文件
            val fileUrl = buildWebdavUrl(webdavUser!!.host, "${webdavUser!!.path}/$name")
            val davResource = DavCollection(httpClient!!, Url(fileUrl))

            var content = ""
            davResource.get(accept = "*/*", headers = null) { response ->
                content = response.bodyAsText()
            }

            val result = restore(content)
            emit(result)
        } catch (e: Exception) {
            emit(false)
            println(e.message)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun loadFileList(path: String) {
        flow {
            if (!checkAndLoadUser() || httpClient == null) {
                emit(listOf())
                return@flow
            }
            try {
                println("loadFileList - path: $path, webdavUser: $webdavUser")
                val list = listFilesWithType(httpClient!!, path, webdavUser)
                emit(list)
            } catch (e: Exception) {
                println("loadFileList error: ${e.message}")
                e.printStackTrace()
                emit(listOf())
            }
        }.flowOn(Dispatchers.IO)
            .collectLatest { _uiDavResourceModel.value = it }
    }

    private suspend fun listFilesWithType(
        client: HttpClient,
        path: String,
        webdavUser: WebdavUser?
    ): List<DavResourceItem> {
        val normalizedHost = webdavUser?.host?.trimEnd('/') ?: ""
        val normalizedPath = path.let { if (it.startsWith("/")) it else "/$it" }
        val filePath = "$normalizedHost$normalizedPath"
        println("listFilesWithType - filePath: $filePath")
        val collection = DavCollection(client, Url(filePath))
        val davResources = mutableListOf<DavResourceItem>()
        collection.propfind(depth = 1) { response, relation ->
            if (relation != HrefRelation.SELF) {
                val decodedHref = try {
                    java.net.URLDecoder.decode(response.href.toString(), "UTF-8")
                } catch (e: Exception) {
                    response.href.toString()
                }
                val resource = DavResource(client, Url(decodedHref))
                val isDirectory = response.properties.any { prop ->
                    when (prop) {
                        is io.github.triangleofice.dav4kmp.property.GetContentType ->
                            prop.type?.contentType == "httpd/unix-directory"

                        else -> prop.toString().contains("collection", ignoreCase = true)
                    }
                }
                davResources.add(DavResourceItem(resource, isDirectory))
            }
        }
        return davResources
    }

    /**
     * 创建 HttpClient
     */
    private fun createHttpClient(name: String, pass: String): HttpClient {
        return HttpClient {
            install(ContentNegotiation)
            // 设置超时时间
            install(HttpTimeout) {
                requestTimeoutMillis = 160000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 160000
            }
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(username = name, password = pass)
                    }
                    sendWithoutRequest { true }
                }
            }
        }
    }

    /**
     * 保存,需要确定path是要可建立目录的权限目录下,坚果云只能在"dav/我的坚果云"路径下才有权限.
     */
    suspend fun saveWebdavUser(name: String, pass: String, host: String, path: String) = flow {
        try {
            val testHttpClient = createHttpClient(name, pass)
            val fullUrl = buildWebdavUrl(host, path)
            val result = testPathOrCreate(testHttpClient, fullUrl)
            println("url:$fullUrl, res:$result")

            if (result) {
                val user = WebdavUser(name, pass, host, path)
                val jsonString = Json.encodeToString(user)

                webdavUser = user
                httpClient = testHttpClient
                davCollection = DavCollection(httpClient!!, Url(fullUrl))

                saveWebdavCacheFile(KEY_CONFIG_USER, jsonString)
                emit(true)
            } else {
                emit(false)
            }
        } catch (e: Exception) {
            println(e)
            emit(false)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 构建 WebDAV URL
     */
    private fun buildWebdavUrl(host: String, path: String): String {
        val normalizedHost = host.trimEnd('/')
        val normalizedPath = path.trim().let {
            if (it.isEmpty()) "" else if (it.startsWith("/")) it else "/$it"
        }
        return "$normalizedHost$normalizedPath"
    }

    companion object {

        const val DEFAULT_JSON = "kreader_lastest.json"
        const val KEY_CONFIG_JSON = "webdav_config_json"
        const val KEY_CONFIG_USER = "webdav_config_user"
        const val KEY_NAME = "name"
        const val KEY_PASS = "pass"
        const val KEY_PATH = "path"
        const val KEY_HOST = "host"

        //const val JIANGUOYUN = "https://dav.jianguoyun.com"
        //const val PATH = "/dav/我的坚果云"
        //const val JIANGUOYUN_URL = "$JIANGUOYUN$PATH"

        /**
         * 测试路径是否存在，不存在则创建
         */
        private suspend fun testPathOrCreate(client: HttpClient, fullUrl: String): Boolean {
            return try {
                val collection = DavCollection(client, Url(fullUrl))

                // 尝试列出目录内容来测试连接
                var exists = false
                try {
                    collection.propfind(depth = 0) { _, _ ->
                        exists = true
                    }
                } catch (_: Exception) {
                    exists = false
                }

                if (!exists) {
                    try {
                        val davResource = DavResource(client, Url(fullUrl))
                        davResource.mkCol(null) { _ ->
                            // 创建成功
                        }
                        true
                    } catch (e: Exception) {
                        println("Failed to create directory: ${e.message}")
                        false
                    }
                } else {
                    true
                }
            } catch (e: Exception) {
                println("WebDAV connection test failed: ${e.message}")
                false
            }
        }

        suspend fun uploadFile(
            client: HttpClient,
            data: ByteArray,
            path: String,
            webdavUser: WebdavUser?
        ) {
            val filePath = "${webdavUser?.host}${webdavUser?.path}/$path"
            val davCollection = DavCollection(client, Url(filePath))
            davCollection.put(
                body = data,
                contentType = io.ktor.http.ContentType.Application.OctetStream
            ) { response ->
                println("Upload file successful: ${response.status}")
            }
        }

        suspend fun uploadFile(
            client: HttpClient,
            fileContent: String,
            path: String,
            webdavUser: WebdavUser?
        ) {
            val filePath = "${webdavUser?.host}${webdavUser?.path}/$path"
            val data = fileContent.toByteArray()
            val davCollection = DavCollection(client, Url(filePath))
            davCollection.put(
                body = data,
                contentType = io.ktor.http.ContentType.Application.OctetStream
            ) { response ->
                println("Upload file successful: ${response.status}")
            }
        }

        suspend fun listFiles(
            client: HttpClient,
            path: String,
            webdavUser: WebdavUser?
        ): MutableList<DavResource> {
            // 处理 host 和 path 的拼接，避免双斜杠
            val normalizedHost = webdavUser?.host?.trimEnd('/') ?: ""
            val normalizedPath = path.let { if (it.startsWith("/")) it else "/$it" }
            val filePath = "$normalizedHost$normalizedPath"
            println("listFiles - filePath: $filePath")
            val collection = DavCollection(client, Url(filePath))
            val davResources = mutableListOf<DavResource>()
            collection.propfind(depth = 1) { response, relation ->
                // 跳过当前目录本身
                if (relation != HrefRelation.SELF) {
                    val resource = DavResource(client, Url(response.href.toString()))
                    // 为目录添加标记，通过检查 contentType 或 collection 属性
                    val isDirectory = response.properties.any { prop ->
                        (prop is io.github.triangleofice.dav4kmp.property.GetContentType &&
                                prop.type?.contentType == "httpd/unix-directory") ||
                                prop.toString().contains("collection", ignoreCase = true)
                    }
                    davResources.add(resource)
                }
            }
            return davResources
        }

        suspend fun downloadFile(
            client: HttpClient,
            name: String,
            webdavUser: WebdavUser?
        ): String {
            val filePath = "${webdavUser?.host}$name"
            println("download:$name")
            val davCollection = DavCollection(client, Url(filePath))
            var fileContent = ""
            davCollection.get(accept = "*/*", headers = null) { response ->
                fileContent = response.bodyAsText()
            }
            return fileContent
        }

        suspend fun deleteFile(client: HttpClient, name: String, webdavUser: WebdavUser?) {
            val filePath = "${webdavUser?.host}$name"
            val davCollection = DavCollection(client, Url(filePath))
            davCollection.delete { response ->
                println("Delete file successful: ${response.status}")
            }
        }

        fun restore(content: String): Boolean {
            /*try {
                val progresses = BookProgressParser.parseProgresses(content)
                Graph.database.runInTransaction {
                    Graph.database.progressDao().deleteAllProgress()
                    Graph.database.progressDao().addProgresses(progresses)
                }
                return true
            } catch (e: Exception) {
                println(e.message)
            }*/
            return false
        }
    }
}
