package com.duqian.coverage_library

import android.content.Context
import android.os.Looper
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Description:覆盖率文件dump，保存、上传
 *
 * Created by 杜乾 on 2024/7/15 - 10:18.
 * E-mail: duqian2010@gmail.com
 */
object JacocoHelper {
    private const val TAG = "dq-jacoco-JacocoHelper"
    private const val SUFFIX = ".ec"//.exec
    private var isOpenCoverage: Boolean = false
    private var currentBranchName = ""
    private var currentCommitId = ""
    private var appName = "" //要跟gradle插件配置的appName一致，作为存储目录

    //是否唯一的名字，最好按照时间戳命名,防止不同的人，不同设备重名覆盖远已经保存了的文件,不过服务器已经对上传的文件做md5，相同的已经存在，不会继续保存。
    private var isSingleFile = true

    //ec文件的路径
    private var mRootDir = ""
    //private val mHandler = Handler(Looper.getMainLooper())

    private var hostUrl = ""//BuildConfig.JACOCO_HOST //服务器地址
    private var mDeviceId: String = ""

    private var mCallback: JacocoCallback? = null

    private fun isMainThread(): Boolean {
        return Looper.getMainLooper() == Looper.myLooper()
    }

    /**
     * 初始化数据，应用初始化后是固定的了
     * 最外层调用的时候先初始化
     */
    fun initAppData(
        isOpenCoverage: Boolean,
        currentBranchName: String = "",
        currentCommitId: String = "",
        appName: String = "",
        hostUrl: String = "",
    ) {
        this.isOpenCoverage = isOpenCoverage
        this.currentBranchName = currentBranchName
        this.currentCommitId = currentCommitId
        this.appName = appName
        this.hostUrl = hostUrl
        this.hostUrl = hostUrl
    }

    fun generateEcFileAndUpload(context: Context?, deviceId: String, callback: JacocoCallback?) {
        mCallback = callback
        mDeviceId = deviceId
        if (isOpenCoverage) {
            //debug下，外部需要子线程调用，生成ec文件并上传
            val isSuccess = generateEcFile(context)
            if (isSuccess) {
                upload()
            }
        } else {
            val errorMsg = "ignored,isOpenCoverage=$isOpenCoverage"
            mCallback?.onIgnored(errorMsg)
        }
    }

    /**
     * 生成ec文件
     * isAppend 是否追加模式写ec文件
     */
    @Synchronized
    fun generateEcFile(context: Context?): Boolean {
        if (!isOpenCoverage || context == null || TextUtils.isEmpty(appName)) {
            mCallback?.onLog(TAG, "generateEcFile return,isOpenCoverage=$isOpenCoverage")
            return false
        }
        if (isMainThread()) {
            throw RuntimeException("can't call on main thread,Please run it in background thread")
        }
        handleOldEcFiles(context)
        var out: OutputStream? = null
        var isSuccess = false
        //每台设备上传不一样的ec文件
        var fileName = "jacoco_${currentCommitId}_$mDeviceId$SUFFIX"
        if (!isSingleFile) {  //按照时间戳命名,防止不同的人，不同设备重名覆盖远已经保存了的文件
            fileName = "$fileName-${System.currentTimeMillis()}$SUFFIX"
        } else {
            /*if (TextUtils.isEmpty(mDeviceId)){
                return false
            }*/
        }
        mRootDir = getJacocoEcFileSaveDir(context)
        val path = mRootDir + fileName
        val mCoverageFile = File(path)
        try {
            File(mRootDir).mkdirs()
            if (!isSingleFile && mCoverageFile.exists()) {
                mCallback?.onLog(TAG, "delete old ec file,$fileName")
                mCoverageFile.delete()
            }
            if (!mCoverageFile.exists()) {
                mCoverageFile.createNewFile()
            }
            out = FileOutputStream(mCoverageFile.path, isSingleFile)
            val agent = Class.forName("org.jacoco.agent.rt.RT")
                .getMethod("getAgent")
                .invoke(null)
            if (agent != null) {
                //args:reset如果为true，则之后清除当前执行数据
                out.write(
                    agent.javaClass.getMethod("getExecutionData", Boolean::class.javaPrimitiveType)
                        .invoke(agent, isSingleFile) as ByteArray
                )
                if (isSingleFile) {
                    mCallback?.onLog(TAG, "generateEcFile success：$fileName")
                }
            } else {
                mCallback?.onLog(TAG, "generateEcFile agent =null")
            }

            mCallback?.onEcDumped(path)
            isSuccess = true
        } catch (e: Exception) {
            val errorMsg = "generateEcFile error=$e"
            mCallback?.onIgnored(errorMsg)
        } finally {
            try {
                out?.close()
            } catch (e: Exception) {
            }
        }
        return isSuccess
    }

    private fun handleOldEcFiles(context: Context) {
        //分支不同，删除旧的覆盖率信息
        val lastBranch = SPUtil.get(context).getString("lastBranch", "")
        if (!TextUtils.isEmpty(lastBranch) && !lastBranch.equals(currentBranchName)) {
            CoverageUtil.deleteDirectory(mRootDir)
            mCallback?.onLog(
                TAG,
                "generateEcFile deleteDirectory,lastBranch=$lastBranch,currentBranchName=$currentBranchName"
            )
        }
        SPUtil.get(context).putString("lastBranch", currentBranchName)
    }

    fun getJacocoEcFileSaveDir(context: Context?): String {
        if (TextUtils.isEmpty(mRootDir)) {
            val root = context?.externalCacheDir?.absolutePath?.toString()
            mRootDir = "$root/connected/"
        }
        return mRootDir
    }

    /**
     * 上传覆盖率文件
     */
    @Synchronized
    private fun upload() {
        try {
            mCallback?.onLog(TAG, "start to upload ec files")
            syncUploadFiles()
            mCallback?.onLog(TAG, "upload finished")
        } catch (e: Exception) {
            val errorMsg = "uploadEcData error:$e"
            mCallback?.onLog(TAG, errorMsg)
            mCallback?.onIgnored(errorMsg)
        }
    }


    /**
     * 文件上传，如果是本地server，要确保测试设备与server在同一个局域网
     * */
    @Throws(Exception::class)
    private fun syncUploadFiles() {
        val client: okhttp3.OkHttpClient = buildHttpClient()
        val dir = File(mRootDir)
        val files = dir.listFiles()
        if (dir.exists() && files?.isNotEmpty() == true) {
            mCallback?.onLog(TAG, "upload ec File list=" + Arrays.toString(files))
            for (f in files) {
                val filename = f.name
                if (!filename.endsWith(SUFFIX) || f.length() <= 0 || !filename.contains(mDeviceId)) {
                    continue
                }
                //application/plain  multipart/form-data
                val fileBody: okhttp3.RequestBody =
                    okhttp3.RequestBody.create(okhttp3.MediaType.parse("multipart/form-data"), f)

                val body: okhttp3.RequestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("file", filename, fileBody)
                    .addFormDataPart("appName", appName)//固定
                    /*.addFormDataPart("uid", userUID)
                    .addFormDataPart("userName", userName)*/
                    .addFormDataPart("branch", currentBranchName)
                    .addFormDataPart("commitId", currentCommitId)
                    .addFormDataPart("type", ".ec")
                    .build()
                val url = "$hostUrl/coverage/upload"
                mCallback?.onLog(
                    TAG,
                    "upload ec file,appName=$appName,file=${f.name},size=${f.length()}.currentCommitId=$currentCommitId"
                )
                mCallback?.onLog(
                    TAG,
                    "upload ec file, currentBranchName=$currentBranchName url =$url"
                )
                val request: okhttp3.Request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                val call: okhttp3.Call = client.newCall(request)
                val response: okhttp3.Response = call.execute()
                handleResponse(response, f)
            }
        }
    }

    private fun handleResponse(response: okhttp3.Response, f: File) {
        var responseBody: okhttp3.ResponseBody? = null
        try {
            responseBody = response.body()
            val str: String = responseBody?.string() ?: ""
            mCallback?.onLog(TAG, "syncUploadFiles str=$str")
            if (response.isSuccessful) {
                if (str.contains("200")) {
                    val name = f.name
                    // ClipboardUtil.setClipboard(null, name)
                    mCallback?.onEcUploaded(isSingleFile, f)
                }
            }
        } catch (e: Exception) {
            val errorMsg = "syncUploadEcFiles error =$e"
            mCallback?.onLog(TAG, errorMsg)
            mCallback?.onIgnored(errorMsg)
        } finally {
            responseBody?.close()
        }
    }

    private fun buildHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun deleteDirectory(filePath: String): Boolean {
        return CoverageUtil.deleteDirectory(filePath)
    }
}