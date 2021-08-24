package com.it22.myapplication

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ActivityCompat
import com.groupdocs.cloud.viewer.api.FileApi
import com.groupdocs.cloud.viewer.api.ViewApi
import com.groupdocs.cloud.viewer.client.ApiException
import com.groupdocs.cloud.viewer.client.Configuration
import com.groupdocs.cloud.viewer.model.*
import com.groupdocs.cloud.viewer.model.requests.CreateViewRequest
import org.apache.commons.io.IOUtils
import java.net.URLEncoder
import android.webkit.*
import com.groupdocs.cloud.viewer.model.requests.UploadFileRequest
import com.groupdocs.cloud.viewer.model.requests.DownloadFileRequest
import java.io.*

import androidx.webkit.WebViewAssetLoader
import java.nio.channels.FileChannel
import java.util.*


class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.name

    // GroupDocs API App
    // Credentials
    var appSid = "ea9fd6cb-fe05-42e8-947f-51dd76cc94be"
    var appKey = "0f1bed7558037a5f77c948bf55fd0662"

    var apiInstance: ViewApi? = null
    var fileApi: FileApi? = null
    var webview: WebView? = null
    val REQ_CODE_PERMISSION = 10101
    var dwgFileSelectionActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    val arrayOfPermissions = arrayOf(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )
    var webviewurl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webview = findViewById<WebView>(R.id.webview)

        val configuration = Configuration(appSid, appKey)
        apiInstance = ViewApi(configuration)
        fileApi = FileApi(configuration)

        dwgFileSelectionActivityResultLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uri = data!!.data
                Thread {
                    try {

                        // Uploading file
                        val filePath = getRealPathFromURI(this, uri!!)
                        val file = File(filePath)
                        fileApi!!.uploadFile(UploadFileRequest(filePath, file, null))
                        Log.d(TAG, "File Uploaded");

                        // Rendering file
                        val fileInfo = FileInfo()
                        fileInfo.filePath = filePath
                        val viewOptions = ViewOptions()
                        viewOptions.fileInfo = fileInfo
                        viewOptions.viewFormat = ViewOptions.ViewFormatEnum.HTML
                        val renderOptions = HtmlOptions()
                        val cadOptions = CadOptions()
                        cadOptions.addLayersItem("TRIANGLE")
                        cadOptions.addLayersItem("QUADRANT")
                        renderOptions.cadOptions = cadOptions
                        viewOptions.renderOptions = renderOptions
                        val response = apiInstance!!.createView(CreateViewRequest(viewOptions))
                        Log.d(TAG, "RenderLayers completed: " + response.pages.size);


                        // Downloading the rendered file and displaying in the webview
                        // For DWG Files, number of pages is always 1
                        // src file is always downloaded in the app cache
                        // We move the file to app internal storage Downloads folder, using 'renameTo' function
                        // We can't load the html file from cache to the webview
                        for (pageView in response.getPages()) {
                            Log.d(TAG, "Starting download")

                            val dlRequest = DownloadFileRequest()
                            dlRequest.setpath(pageView.path)
                            val srcFile = fileApi!!.downloadFile(dlRequest)

                            Log.d(TAG, "Completed download")


                            val dstFile = File(
                                this.filesDir.absolutePath,
                                "Downloads/page_" + pageView.number + "_" + Calendar.getInstance().timeInMillis + ".html"
                            )

                            if (dstFile.exists()) {
                                dstFile.delete()
                                Log.e(TAG, "File already exists")
                            }

                            var success = srcFile.renameTo(dstFile)
                            if (!success)
                                Log.e(TAG, "File wasn't successfully renamed")

                            Log.d(TAG, "Saved page at: ${dstFile.absolutePath}")
                            webviewurl = Uri.fromFile(dstFile).toString()
                            loadWebView(webviewurl)
                        }
                    } catch (e: ApiException) {
                        Log.d(TAG, "Exception: " + e.message)
                        e.printStackTrace()
                    }
                }.start()
            }
        }

        if (!checkPermission(arrayOfPermissions)) {
            getPermission(arrayOfPermissions)
        } else {
            selectDwgFile()
        }
    }

    fun loadWebView(url: String) {

        webview!!.post(Runnable() {

            // Following commented code is used for loading HTML files from the app assets
            /* val assetLoader: WebViewAssetLoader = Builder()
                 .addPathHandler(
                     "/assets/",
                     WebViewAssetLoader.AssetsPathHandler(this)
                 ).build()*/

            val publicDir = File(this.dataDir, "Downloads")
            val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/Downloads/",
                    WebViewAssetLoader.InternalStoragePathHandler(this, publicDir)
                ).build()


            // Override WebView client, and if request is to local file, intercept and serve local
            webview!!.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    Log.d(TAG, "Intercepting requests ${request.url}")
                    return assetLoader.shouldInterceptRequest(request.url);
                }
            }

            Log.d(TAG, "Loading URL: ${webviewurl}")
            webview!!.loadUrl(url)
        });
    }

    fun selectDwgFile() {
        var chooseFile = Intent(Intent.ACTION_OPEN_DOCUMENT)
        chooseFile.type = "*/*"
        chooseFile = Intent.createChooser(chooseFile, "Choose a DWG file")
        dwgFileSelectionActivityResultLauncher!!.launch(chooseFile)
    }

    @SuppressLint("NewApi")
    fun getRealPathFromURI(context: Context, uri: Uri): String {
        val isKitKat: Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) { //main if start

            // DocumentProvider
            if (isGoogleDriveUri(uri)) {
                return getDriveFilePath(context, uri)
            } else if (isExternalStorageDocument(uri)) {// ExternalStorageProvider

                val docId: String = DocumentsContract.getDocumentId(uri)
                val split: List<String> = docId.split(":")
                val type: String = split[0]
                // This is for checking Main Memory
                if ("primary".equals(type, ignoreCase = true)) {
                    if (split.size > 1) {
                        return context.getExternalFilesDir(null).toString() + "/" + split[1]
                    } else {
                        return context.getExternalFilesDir(null).toString() + "/"
                    }
                    // This is for checking SD Card
                } else {
                    return "storage" + "/" + docId.replace(":", "/")
                }
            } else if (isDownloadsDocument(uri)) {
                // DownloadsProvider
                val parcelFileDescriptor =
                    context.contentResolver.openFileDescriptor(uri, "r", null)
                parcelFileDescriptor?.let {
                    val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
                    val file = File(
                        context.cacheDir,
                        context.contentResolver.getFileName(uri)
                    )
                    val outputStream = FileOutputStream(file)
                    IOUtils.copy(inputStream, outputStream)
                    return file.path
                }
            } else if (isMediaDocument(uri)) {
                val docId: String = DocumentsContract.getDocumentId(uri)
                val split: List<String> = docId.split(":")
                val type: String = split[0]
                return copyFileToInternalStorage(context, uri, "dwgviewer")

            }
        }//main if end
        else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // MediaStore (and general)
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment!!
            else copyFileToInternalStorage(
                context,
                uri,
                "dwgviewer"
            )
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            // File
            return uri.path!!
        }
        return null!!
    }

    fun ContentResolver.getFileName(fileUri: Uri): String {
        var name = ""
        val returnCursor = this.query(fileUri, null, null, null, null)
        if (returnCursor != null) {
            val nameIndex = returnCursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )
            returnCursor.moveToFirst()
            name = returnCursor.getString(nameIndex)
            returnCursor.close()
        }
        return URLEncoder.encode(name, "utf-8")
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents".equals(uri.authority)
    }

    fun isGoogleDriveUri(uri: Uri): Boolean {
        return "com.google.android.apps.docs.storage".equals(uri.authority)
                || "com.google.android.apps.docs.storage.legacy".equals(uri.authority)
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents"
            .equals(uri.authority)
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents"
            .equals(uri.authority)
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content"
            .equals(uri.authority)
    }


    fun getDriveFilePath(context: Context, uri: Uri): String {
        val returnUri = uri
        val returnCursor: Cursor? = context.contentResolver.query(returnUri, null, null, null, null)

        val nameIndex: Int = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex: Int = returnCursor.getColumnIndex(OpenableColumns.SIZE)

        returnCursor.moveToFirst()
        val name: String = (returnCursor.getString(nameIndex))
        val file = File(
            context.cacheDir,
            URLEncoder.encode(name, "utf-8")
        )
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            val read: Int = 0
            val maxBufferSize: Int = 1 * 1024 * 1024
            val bytesAvailable: Int = inputStream!!.available()
            //int bufferSize = 1024;
            val bufferSize: Int =
                Math.min(bytesAvailable, maxBufferSize)
            val buffers = ByteArray(bufferSize)
            inputStream.use { inputStream: InputStream ->
                outputStream.use { fileOut ->
                    while (true) {
                        val length = inputStream.read(buffers)
                        if (length <= 0)
                            break
                        fileOut.write(buffers, 0, length)
                    }
                    fileOut.flush()
                    fileOut.close()
                }
            }

            Log.d("File Size", "Size " + file.length())
            inputStream.close()
            Log.d("File Path", "Path " + file.path)
            Log.d("File Size", "Size " + file.length())
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
        }
        return file.path
    }

    fun copyFileToInternalStorage(mContext: Context, uri: Uri, newDirName: String): String {
        val returnCursor: Cursor? = mContext.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )

        val nameIndex = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        val size = java.lang.Long.toString(returnCursor.getLong(sizeIndex))
        val output: File
        if (newDirName != "") {
            val dir = File(
                mContext.filesDir.toString()
                        + "/" + newDirName
            )
            if (!dir.exists()) {
                dir.mkdir()
            }
            output = File(
                mContext.filesDir.toString()
                        + "/" + newDirName + "/"
                        + URLEncoder.encode(name, "utf-8")
            )
        } else {
            output = File(
                mContext.filesDir.toString()
                        + "/" + URLEncoder.encode(name, "utf-8")
            )
        }
        try {
            val inputStream: InputStream? =
                mContext.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(output)
            var read = 0
            val bufferSize = 1024
            val buffers = ByteArray(bufferSize)
            while (inputStream!!.read(buffers).also { read = it } != -1) {
                outputStream.write(buffers, 0, read)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: java.lang.Exception) {
            Log.d("Exception", e.message!!)
        }
        return output.path
    }

    fun checkPermission(permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermission(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, REQ_CODE_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_CODE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectDwgFile()
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Throws(IOException::class)
    fun copy(src: File?, dst: File?) {
        Runnable {
            val inStream = FileInputStream(File(cacheDir, src?.name))
            val outStream = FileOutputStream(File(filesDir, dst?.name))
            val inChannel: FileChannel = inStream.channel
            val outChannel: FileChannel = outStream.channel
            inChannel.transferTo(0, inChannel.size(), outChannel)
            inStream.close()
            outStream.close()
        }
    }


}