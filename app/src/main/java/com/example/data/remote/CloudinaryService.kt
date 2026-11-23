package com.example.data.remote

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloudinary media uploader (§0).
 * - Public assets (profile photos, org/store logos) use unsigned preset `ml_default` on cloud `dlsiskxua`.
 * - Private documents (leave attachments, salary slips) ALWAYS request a server signature via
 *   Cloud Function `signCloudinaryUpload` and NEVER use the unsigned preset.
 */
@Singleton
class CloudinaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    suspend fun uploadPublicImage(
        uri: Uri,
        folder: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readUriBytes(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UNSIGNED_PUBLIC_PRESET)
                .addFormDataPart("folder", "attendance_pro/public/$folder")
                .addFormDataPart(
                    "file",
                    "asset_${System.currentTimeMillis()}.jpg",
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Public upload failed (${response.code}): $raw")
                }
                val json = JSONObject(raw)
                json.getString("secure_url")
            }
        }
    }

    suspend fun uploadPrivateDocument(
        uri: Uri,
        folder: String,
        resourceType: String = "auto",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val signed = cloudFunctions.signCloudinaryUpload(
                folder = "attendance_pro/private/$folder",
                resourceType = resourceType,
            )
            val signature = signed["signature"]?.toString()
                ?: error("Missing upload signature from backend")
            val timestamp = signed["timestamp"]?.toString()
                ?: error("Missing timestamp from backend")
            val apiKey = signed["apiKey"]?.toString()
                ?: error("Missing Cloudinary API key from backend")
            val signedFolder = signed["folder"]?.toString()
                ?: "attendance_pro/private/$folder"
            val cloudName = signed["cloudName"]?.toString() ?: CLOUD_NAME

            val bytes = readUriBytes(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("signature", signature)
                .addFormDataPart("folder", signedFolder)
                .addFormDataPart("type", "authenticated")
                .addFormDataPart(
                    "file",
                    "doc_${System.currentTimeMillis()}",
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Signed upload failed (${response.code}): $raw")
                }
                val json = JSONObject(raw)
                json.getString("secure_url")
            }
        }
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected file")
        return stream.use { it.readBytes() }
    }

    companion object {
        const val CLOUD_NAME = "dlsiskxua"
        const val UNSIGNED_PUBLIC_PRESET = "ml_default"
    }
}
