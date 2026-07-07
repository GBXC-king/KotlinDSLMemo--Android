package com.example.memo.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.URLName
import android.content.ContentResolver
import android.provider.OpenableColumns
import timber.log.Timber

/**
 * 邮件发送工具
 * 用于将日志文件通过邮件发送
 */
object EmailSender {

    /**
     * 发送邮件（带附件）
     * @param context 上下文
     * @param smtpHost SMTP 服务器地址
     * @param smtpPort SMTP 端口
     * @param username 发件人邮箱
     * @param password 邮箱密码或授权码
     * @param toEmail 收件人邮箱
     * @param subject 邮件主题
     * @param body 邮件正文
     * @param attachmentUri 附件 URI（可选）
     * @return Pair(是否成功, 错误信息)
     */
    suspend fun sendEmail(
        context: Context,
        smtpHost: String,
        smtpPort: String,
        username: String,
        password: String,
        toEmail: String,
        subject: String,
        body: String,
        attachmentUri: Uri? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort)
                put("mail.smtp.auth", "true")

                // 根据端口选择 SSL 或 STARTTLS
                when (smtpPort) {
                    "465", "994" -> {
                        // SSL 端口（QQ邮箱、Gmail 等）
                        put("mail.smtp.ssl.enable", "true")
                        put("mail.smtp.socketFactory.port", smtpPort)
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    }
                    "587", "25" -> {
                        // STARTTLS 端口
                        put("mail.smtp.starttls.enable", "true")
                    }
                    else -> {
                        // 默认尝试 STARTTLS
                        put("mail.smtp.starttls.enable", "true")
                    }
                }
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                setSubject(subject)

                val multipart: Multipart = MimeMultipart()

                // 添加正文
                val bodyPart = MimeBodyPart()
                bodyPart.setText(body)
                multipart.addBodyPart(bodyPart)

                // 添加附件
                if (attachmentUri != null) {
                    val inputStream = context.contentResolver.openInputStream(attachmentUri)
                    if (inputStream != null) {
                        // 先将附件读入内存，避免 InputStream 在发送过程中被关闭
                        val attachmentBytes = inputStream.readBytes()
                        inputStream.close()

                        val fileName = getFileName(context, attachmentUri) ?: "日志文件.zip"
                        val dataSource = ByteArrayDataSource(attachmentBytes, "application/zip").apply {
                            this.fileName = fileName
                        }
                        val attachmentPart = MimeBodyPart()
                        attachmentPart.dataHandler = javax.activation.DataHandler(dataSource)
                        attachmentPart.fileName = fileName
                        multipart.addBodyPart(attachmentPart)
                    }
                }

                setContent(multipart)
            }

            Transport.send(message)
            Pair(true, "发送成功")
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("AuthenticationFailed") == true -> "邮箱授权码错误"
                e.message?.contains("ConnectException") == true -> "无法连接到SMTP服务器，请检查网络"
                e.message?.contains("SocketTimeoutException") == true -> "连接超时，请检查SMTP服务器地址"
                e.message?.contains("SSLHandshakeException") == true -> "SSL握手失败，请检查端口配置"
                else -> e.message ?: "未知错误"
            }
            Timber.tag("EmailSender").e(e, "发送邮件失败: $errorMsg")
            Pair(false, errorMsg)
        }
    }

    /**
     * 获取文件名
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    /**
     * 自定义数据源类
     */
    private class ByteArrayDataSource(
        private val data: ByteArray,
        private val type: String
    ) : javax.activation.DataSource {
        var fileName: String = "attachment"

        override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream(data)
        override fun getOutputStream(): java.io.OutputStream {
            throw UnsupportedOperationException("Read-only data source")
        }
        override fun getContentType(): String = type
        override fun getName(): String = fileName
    }
}
