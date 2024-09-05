package com.my.survey.shared_data.types.codecs

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.tessellation.json.JsonSerializer

object JsonBinaryCodec {
  def forSync[F[_]: Sync]: F[JsonSerializer[F]] = Sync[F].delay {
    new JsonSerializer[F] {
      def serialize[A: Encoder](a: A): F[Array[Byte]] =
        Sync[F].delay(io.circe.jawn.stringify(Encoder[A].apply(a)).getBytes)

      def deserialize[A: Decoder](bytes: Array[Byte]): F[A] =
        Sync[F].fromEither(io.circe.jawn.decode[A](new String(bytes)))
    }
  }
}