package io.github.nandotorterolo

package object crypto {

  type Cripto[F[_]] = Encryption[F] with EncryptionUtil[F]

}
