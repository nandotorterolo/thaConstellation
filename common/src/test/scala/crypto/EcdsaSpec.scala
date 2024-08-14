package crypto

import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.Signature

import scala.util.Try

import munit.CatsEffectSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.ECNamedCurveTable

class EcdsaSpec extends CatsEffectSuite {

  override def beforeAll(): Unit = {
    Security.addProvider(new BouncyCastleProvider())
    ()
  }

  test("Verify from X509EncodedKeySpec getPublic getEncoded") {

    val kpInstance = KeyPairGenerator.getInstance("ECDSA", "BC")
    kpInstance.initialize(ECNamedCurveTable.getParameterSpec("B-571"), new SecureRandom())
    val kp = kpInstance.generateKeyPair()

    val ecdsaSignature = Signature.getInstance("SHA256withECDSA", "BC")

    ecdsaSignature.initSign(kp.getPrivate)
    ecdsaSignature.update("hello".getBytes)
    val signatureSignedA = ecdsaSignature.sign()

    val kf               = KeyFactory.getInstance("ECDSA", "BC")
    val formatted_public = new X509EncodedKeySpec(kp.getPublic.getEncoded)
    ecdsaSignature.initVerify(kf.generatePublic(formatted_public))
    ecdsaSignature.update("hello".getBytes)
    val res = ecdsaSignature.verify(signatureSignedA)

    assert(res)

  }

  test("Verify from PKCS8EncodedKeySpec getEncoded") {

    val kpInstance = KeyPairGenerator.getInstance("ECDSA", "BC")
    kpInstance.initialize(ECNamedCurveTable.getParameterSpec("B-571"), new SecureRandom())
    val kp = kpInstance.generateKeyPair()

    val ecdsaSignature = Signature.getInstance("SHA256withECDSA", "BC")

    val formatted_private = new PKCS8EncodedKeySpec(kp.getPrivate.getEncoded)
    val kf                = KeyFactory.getInstance("ECDSA", "BC");
    ecdsaSignature.initSign(kf.generatePrivate(formatted_private))
    ecdsaSignature.update("hello".getBytes)
    val signatureSignedA = ecdsaSignature.sign()

    ecdsaSignature.initVerify(kp.getPublic)
    ecdsaSignature.update("hello".getBytes)
    val res = ecdsaSignature.verify(signatureSignedA)

    assert(res)

  }

  test("Get public and provate from resource") {
    val kf: KeyFactory = KeyFactory.getInstance("ECDSA", "BC")

    val filePrivate = this.getClass.getClassLoader.getResourceAsStream("keys/a").readAllBytes()
    val res         = Try(kf.generatePrivate(new PKCS8EncodedKeySpec(filePrivate))).toOption.isDefined

    val filePublic = this.getClass.getClassLoader.getResourceAsStream("keys/a.pub").readAllBytes()
    val res2       = Try(kf.generatePublic(new X509EncodedKeySpec(filePublic))).toOption.isDefined
    assert(res && res2)
  }
}
