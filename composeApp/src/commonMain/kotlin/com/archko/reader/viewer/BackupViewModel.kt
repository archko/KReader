package com.archko.reader.viewer

import androidx.lifecycle.ViewModel
import com.archko.reader.pdf.cache.saveWebdavCacheFile
import io.github.triangleofice.dav4kmp.DavCollection
import io.github.triangleofice.dav4kmp.DavResource
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

/**
 * @author: archko 2020/11/16 :11:23
 */
class BackupViewModel : ViewModel() {

    //private val progressDao by lazy { Graph.database.progressDao() }
    var webdavUser: WebdavUser? = null
    private var httpClient: HttpClient? = null
    private var davCollection: DavCollection? = null

    private val _uiDavResourceModel = MutableStateFlow<List<DavResource>?>(null)
    val uiDavResourceModel: StateFlow<List<DavResource>?>
        get() = _uiDavResourceModel.asStateFlow()

    //private val _uiRestoreModel = MutableStateFlow<ResponseHandler<Boolean>?>(null)
    //val uiRestoreModel: StateFlow<ResponseHandler<Boolean>?>
    //    get() = _uiRestoreModel.asStateFlow()

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
            val davResource = DavResource(httpClient!!, Url(fileUrl))

            davResource.put(
                body = content.toByteArray(),
                contentType = ContentType.Application.Json
            ) { response ->
                // 上传成功回调
            }*/
            emit(true)
        } catch (e: Exception) {
            emit(false)
            println(e.message)
        }
    }.flowOn(Dispatchers.IO)

    fun checkAndLoadUser(): Boolean {
        /*if (null == webdavUser) {
            val mmkv = MMKV.mmkvWithID(KEY_CONFIG_JSON)
            val content = mmkv.decodeString(KEY_CONFIG_USER)
            if (!TextUtils.isEmpty(content)) {
                Logcat.d(content)
                val jsonObject = JSONObject(content!!)
                val name = jsonObject.optString(KEY_NAME)
                val pass = jsonObject.optString(KEY_PASS)
                val host = jsonObject.optString(KEY_HOST)
                val path = jsonObject.optString(KEY_PATH)
                if (TextUtils.isEmpty() || TextUtils.isEmpty(pass)
                    || TextUtils.isEmpty(host) || TextUtils.isEmpty(path)
                ) {
                    return false
                }
                webdavUser = WebdavUser(name, pass, host, path)
                httpClient = createHttpClient(name, pass)
                davCollection = DavCollection(httpClient!!, Url(buildWebdavUrl(host, path)))
                return true
            }
            return false
        }*/
        return true
    }

    /*suspend fun restoreFromWebdav(name: String) = flow {
        try {
            if (!checkAndLoadUser() || davCollection == null) {
                //emit(ResponseHandler.Failure())
                return@flow
            }

            // 使用 dav4kmp 下载文件
            val fileUrl = buildWebdavUrl(webdavUser!!.host, "${webdavUser!!.path}/$name")
            val davResource = DavResource(httpClient!!, Url(fileUrl))

            var content = ""
            davResource.get(
                accept = "application/json",
                headers = null
            ) { response ->
                content = response.bodyAsText()
            }

            val result = restore(content)
            emit(ResponseHandler.Success(result))
        } catch (e: JSONException) {
            emit(ResponseHandler.Failure())
            println(e.message)
        } catch (e: Exception) {
            emit(ResponseHandler.Failure())
            println(e.message)
        }
    }.flowOn(Dispatchers.IO)
        .collectLatest { _uiRestoreModel.value = it }*/

    suspend fun webdavBackupFiles(path: String) =
        flow {
            if (!checkAndLoadUser() || davCollection == null) {
                emit(listOf())
                return@flow
            }
            var list: MutableList<DavResource>? = null
            try {
                // 使用 dav4kmp 列出文件
                val fullUrl = buildWebdavUrl(webdavUser!!.host, path)
                val collection = DavCollection(httpClient!!, Url(fullUrl))

                val davResources = mutableListOf<DavResource>()
                collection.propfind(depth = 1) { response, relation ->
                    // 收集响应中的资源
                    davResources.add(DavResource(httpClient!!, Url(response.href.toString())))
                }

                list = davResources
            } catch (e: Exception) {
                println(e.message)
            }

            emit(list)
        }.flowOn(Dispatchers.IO)
            .collectLatest { _uiDavResourceModel.value = it }

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
                val jsonString = buildString {
                    append("{")
                    append("\"$KEY_NAME\":\"$name\",")
                    append("\"$KEY_PASS\":\"$pass\",")
                    append("\"$KEY_HOST\":\"$host\",")
                    append("\"$KEY_PATH\":\"$path\"")
                    append("}")
                }

                // 更新当前用户和客户端
                webdavUser = WebdavUser(name, pass, host, path)
                httpClient = testHttpClient
                davCollection = DavCollection(httpClient!!, Url(fullUrl))
                //val resource = listFiles(testHttpClient, path, webdavUser)
                //println("resource:$resource")

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

        const val DEFAULT_JSON = "amupdf_lastest.json"
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
                    // 目录不存在，尝试创建
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
            val davResource = DavResource(client, Url(filePath))
            davResource.put(
                body = data,
                contentType = io.ktor.http.ContentType.Application.OctetStream
            ) { }
        }

        suspend fun uploadFile(
            client: HttpClient,
            fileContent: String,
            path: String,
            webdavUser: WebdavUser?
        ) {
            val filePath = "${webdavUser?.host}${webdavUser?.path}/$path"
            val data = fileContent.toByteArray()
            val davResource = DavResource(client, Url(filePath))
            davResource.put(
                body = data,
                contentType = io.ktor.http.ContentType.Application.Json
            ) { }
        }

        suspend fun listFiles(
            client: HttpClient,
            path: String,
            webdavUser: WebdavUser?
        ): MutableList<DavResource> {
            val filePath = "${webdavUser?.host}$path"
            val collection = DavCollection(client, Url(filePath))
            val davResources = mutableListOf<DavResource>()
            collection.propfind(depth = 1) { response, _ ->
                davResources.add(DavResource(client, Url(response.href.toString())))
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
            val davResource = DavResource(client, Url(filePath))
            var fileContent = ""
            davResource.get(accept = "*/*", headers = null) { response ->
                fileContent = response.bodyAsText()
            }
            return fileContent
        }

        suspend fun deleteFile(client: HttpClient, name: String, webdavUser: WebdavUser?) {
            val filePath = "${webdavUser?.host}$name"
            val davResource = DavResource(client, Url(filePath))
            davResource.delete { }
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
