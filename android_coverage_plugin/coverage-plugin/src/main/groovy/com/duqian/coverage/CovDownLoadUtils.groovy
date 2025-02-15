package com.duqian.coverage

import okhttp3.*

import java.util.concurrent.TimeUnit

/**
 * Description:gradle下载工具:network同步ec或者pull sdcard里面的ec文件
 * Created by 杜乾 on 2024/7/15 - 10:20.
 * E-mail: duqian2010@gmail.com
 */
class CovDownLoadUtils {

    private static String TAG = CovDownLoadUtils.simpleName
    /**
     * 文件上传，如果是本地server，要确保测试设备与server在同一个局域网
     * */
    static void downloadFile(String downloadUrl, File file) {
        try {
            OkHttpClient client = buildHttpClient()
            String url = downloadUrl
            println("$TAG downloadFile url=$url")
            Request request = new Request.Builder()
                    .url(url)
                    .build()
            Call call = client.newCall(request)
            Response response = call.execute()

            handleResponse(response, file)

            /*def ecFileText = response.body().string()
            println "$TAG downloadFile string:${ecFileText}"
            FileUtil.saveStringToFile(ecFileText,file)*/
        } catch (Exception e) {
            println "$TAG downloadFile e:$e"
        }
    }

    private static void handleResponse(Response response, File target) {
        ResponseBody responseBody = null
        InputStream is = null
        OutputStream os = null
        try {
            responseBody = response.body()
            is = responseBody.byteStream()
            os = new FileOutputStream(target)
            int code = response.code()
            if (code != 200) {
                println("$TAG download failed=${responseBody.string()}")
                return
            }
            File parentFile = target.getParentFile()
            if (parentFile != null && !parentFile.exists()) {
                parentFile.mkdirs()
            }
            if (!target.exists()) {
                target.createNewFile()
            } else {
                //target.delete()
            }
            target.exists()
            byte[] buffer = new byte[1024 * 2]
            int length
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length)
            }
            println("$TAG target.exists()=${target.exists()},downloaded =${target}")
        } catch (Exception e) {
            println("$TAG download error =$e")
        } finally {
            responseBody.close()
            is.close()
            os.close()
        }
    }

    private static OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(5 * 60L, TimeUnit.SECONDS)
                .build()
    }
}
