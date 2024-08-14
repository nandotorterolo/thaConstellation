package io.github.nandotorterolo.node.interfaces

import java.security.PrivateKey
import java.security.PublicKey

import io.github.nandotorterolo.models.ModelThrowable

trait ServerCredentials[F[_]] {

  /**
   * Get the Server Provite Key
   * @return
   */
  def getPrivateKey: F[Either[ModelThrowable, PrivateKey]]

  /**
   * Get the Server Prublic
   * @return
   */
  def getPublicKey: F[Either[ModelThrowable, PublicKey]]

}
