package io.github.olivierlemasle.ca;

import io.github.olivierlemasle.ca.Signer.SignerWithSerial;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.joda.time.DateTime;

class SignerImpl implements Signer, SignerWithSerial {
  private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

  private final SerialNumberGenerator snGen;
  private final KeyPair signerKeyPair;
  private final DistinguishedName signerDn;
  private final PublicKey publicKey;
  private final DistinguishedName dn;
  private final List<CertExtension> extensions = new ArrayList<>();

  private BigInteger serialNumber;
  private DateTime notBefore = DateTime.now().withTimeAtStartOfDay();
  private DateTime notAfter = notBefore.plusYears(1);

  SignerImpl(final SerialNumberGenerator snGen, final KeyPair signerKeyPair,
      final DistinguishedName signerDn, final PublicKey publicKey, final DistinguishedName dn) {
    this.snGen = snGen;
    this.signerKeyPair = signerKeyPair;
    this.signerDn = signerDn;
    this.publicKey = publicKey;
    this.dn = dn;
  }

  @Override
  public SignerWithSerial setSerialNumber(final BigInteger serialNumber) {
    this.serialNumber = serialNumber;
    return this;
  }

  @Override
  public SignerWithSerial setRandomSerialNumber() {
    this.serialNumber = snGen.generateRandomSerialNumber();
    return this;
  }

  @Override
  public SignerWithSerial setNotBefore(final DateTime notBefore) {
    this.notBefore = notBefore;
    return this;
  }

  @Override
  public SignerWithSerial setNotAfter(final DateTime notAfter) {
    this.notAfter = notAfter;
    return this;
  }

  @Override
  public SignerWithSerial validDuringYears(final int years) {
    notAfter = notBefore.plusYears(years);
    return this;
  }

  @Override
  public SignerWithSerial addExtension(final CertExtension extension) {
    extensions.add(extension);
    return this;
  }

  @Override
  public SignerWithSerial addExtension(final ASN1ObjectIdentifier oid, final boolean isCritical,
      final ASN1Encodable value) {
    extensions.add(new CertExtension(oid, isCritical, value));
    return this;
  }

  @Override
  public X509Certificate sign() {
    try {
      final ContentSigner sigGen = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
          .build(signerKeyPair.getPrivate());

      final SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(
          publicKey.getEncoded());

      final JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
      final X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
          signerDn.getX500Name(),
          serialNumber,
          notBefore.toDate(),
          notAfter.toDate(),
          dn.getX500Name(),
          subPubKeyInfo)
          .addExtension(Extension.authorityKeyIdentifier, false,
              extUtils.createAuthorityKeyIdentifier(signerKeyPair.getPublic()))
          .addExtension(Extension.subjectKeyIdentifier, false,
              extUtils.createSubjectKeyIdentifier(publicKey));

      for (final CertExtension e : extensions) {
        certBuilder.addExtension(e.getOid(), e.isCritical(), e.getValue());
      }

      final X509CertificateHolder holder = certBuilder.build(sigGen);
      final X509Certificate cert = new JcaX509CertificateConverter()
          .getCertificate(holder);

      cert.checkValidity();
      cert.verify(signerKeyPair.getPublic());

      return cert;
    } catch (final OperatorCreationException | CertificateException | InvalidKeyException
        | NoSuchAlgorithmException | NoSuchProviderException | SignatureException
        | CertIOException e) {
      throw new CaException(e);
    }
  }

}
