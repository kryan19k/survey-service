package com.my.survey.shared_data.encryption

import cats.effect.Sync
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64

import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object Encryption {
  Security.addProvider(new BouncyCastleProvider())

  private val algorithm = "AES"
  private val transformation = "AES/ECB/PKCS5Padding"

  def encrypt[F[_]: Sync](data: String, key: String): F[String] =
    Sync[F].delay {
      val cipher = Cipher.getInstance(transformation, "BC")
      val secretKey = new SecretKeySpec(key.getBytes("UTF-8"), algorithm)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      Base64.toBase64String(cipher.doFinal(data.getBytes("UTF-8")))
    }

  def decrypt[F[_]: Sync](encryptedData: String, key: String): F[String] =
    Sync[F].delay {
      val cipher = Cipher.getInstance(transformation, "BC")
      val secretKey = new SecretKeySpec(key.getBytes("UTF-8"), algorithm)
      cipher.init(Cipher.DECRYPT_MODE, secretKey)
      new String(cipher.doFinal(Base64.decode(encryptedData)))
    }

  // survey-specific encryption methods

  def encryptSurveyResponse[F[_]: Sync](response: String, publicKey: String): F[String] =
    encrypt(response, publicKey)

  def decryptSurveyResponse[F[_]: Sync](encryptedResponse: String, privateKey: String): F[String] =
    decrypt(encryptedResponse, privateKey)

  def generateEncryptionKey[F[_]: Sync]: F[String] =
    Sync[F].delay {
      val keyBytes = new Array[Byte](16) // 128 bits
      new java.security.SecureRandom().nextBytes(keyBytes)
      Base64.toBase64String(keyBytes)
    }
}