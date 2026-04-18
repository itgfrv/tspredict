package com.gafarov.tspredict.service

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class MinioStorageService(
    private val minioClient: MinioClient,
    @Value("\${app.storage.minio.bucket}")
    private val bucketName: String
) {

    fun ensureBucketExists() {
        val exists = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(bucketName).build()
        )
        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(bucketName).build()
            )
        }
    }

    fun uploadInputStream(
        objectName: String,
        inputStream: InputStream,
        size: Long,
        contentType: String
    ): String {
        ensureBucketExists()

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build()
        )

        return "$bucketName/$objectName"
    }

    fun uploadBytes(
        objectName: String,
        bytes: ByteArray,
        contentType: String
    ): String {
        return uploadInputStream(
            objectName = objectName,
            inputStream = bytes.inputStream(),
            size = bytes.size.toLong(),
            contentType = contentType
        )
    }

    fun getObject(path: String): InputStream {
        val objectName = extractObjectName(path)

        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .build()
        )
    }

    private fun extractObjectName(path: String): String {
        val prefix = "$bucketName/"
        return if (path.startsWith(prefix)) {
            path.removePrefix(prefix)
        } else {
            path
        }
    }
}