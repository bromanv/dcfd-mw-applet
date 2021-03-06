/*
 * eID Applet Project.
 * Copyright (C) 2009 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package test.unit.be.fedict.eid.applet.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.joda.time.DateTime;

public class MiscTestUtils {

	private MiscTestUtils() {
		super();
	}

	public static KeyPair generateKeyPair() throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		SecureRandom random = new SecureRandom();
		keyPairGenerator.initialize(new RSAKeyGenParameterSpec(1024,
				RSAKeyGenParameterSpec.F4), random);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		return keyPair;
	}

	public static X509Certificate generateCertificate(
			PublicKey subjectPublicKey, String subjectDn, DateTime notBefore,
			DateTime notAfter, X509Certificate issuerCertificate,
			PrivateKey issuerPrivateKey, boolean caFlag, int pathLength,
			String crlUri, String ocspUri) throws IOException,
			InvalidKeyException, IllegalStateException,
			NoSuchAlgorithmException, SignatureException, CertificateException {
		String signatureAlgorithm = "SHA1withRSA";
		X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();
		certificateGenerator.reset();
		certificateGenerator.setPublicKey(subjectPublicKey);
		certificateGenerator.setSignatureAlgorithm(signatureAlgorithm);
		certificateGenerator.setNotBefore(notBefore.toDate());
		certificateGenerator.setNotAfter(notAfter.toDate());
		X509Principal issuerDN;
		if (null != issuerCertificate) {
			issuerDN = new X509Principal(issuerCertificate
					.getSubjectX500Principal().toString());
		} else {
			issuerDN = new X509Principal(subjectDn);
		}
		certificateGenerator.setIssuerDN(issuerDN);
		certificateGenerator.setSubjectDN(new X509Principal(subjectDn));
		certificateGenerator.setSerialNumber(new BigInteger(128,
				new SecureRandom()));

		certificateGenerator.addExtension(X509Extensions.SubjectKeyIdentifier,
				false, createSubjectKeyId(subjectPublicKey));
		PublicKey issuerPublicKey;
		issuerPublicKey = subjectPublicKey;
		certificateGenerator.addExtension(
				X509Extensions.AuthorityKeyIdentifier, false,
				createAuthorityKeyId(issuerPublicKey));

		if (caFlag) {
			if (-1 == pathLength) {
				certificateGenerator.addExtension(
						X509Extensions.BasicConstraints, false,
						new BasicConstraints(true));
			} else {
				certificateGenerator.addExtension(
						X509Extensions.BasicConstraints, false,
						new BasicConstraints(pathLength));
			}
		}

		if (null != crlUri) {
			GeneralName gn = new GeneralName(
					GeneralName.uniformResourceIdentifier, new DERIA5String(
							crlUri));

            GeneralNames gns = new GeneralNames(gn);
			DistributionPointName dpn = new DistributionPointName(0, gns);
			DistributionPoint distp = new DistributionPoint(dpn, null, null);
			certificateGenerator.addExtension(
					X509Extensions.CRLDistributionPoints, false,
					new DERSequence(distp));
		}

		if (null != ocspUri) {
			GeneralName ocspName = new GeneralName(
					GeneralName.uniformResourceIdentifier, ocspUri);
			AuthorityInformationAccess authorityInformationAccess = new AuthorityInformationAccess(
					X509ObjectIdentifiers.ocspAccessMethod, ocspName);
			certificateGenerator.addExtension(
					X509Extensions.AuthorityInfoAccess.getId(), false,
					authorityInformationAccess);
		}

		X509Certificate certificate;
		certificate = certificateGenerator.generate(issuerPrivateKey);

		/*
		 * Next certificate factory trick is needed to make sure that the
		 * certificate delivered to the caller is provided by the default
		 * security provider instead of BouncyCastle. If we don't do this trick
		 * we might run into trouble when trying to use the CertPath validator.
		 */
		CertificateFactory certificateFactory = CertificateFactory
				.getInstance("X.509");
		certificate = (X509Certificate) certificateFactory
				.generateCertificate(new ByteArrayInputStream(certificate
						.getEncoded()));
		return certificate;
	}

	private static SubjectKeyIdentifier createSubjectKeyId(PublicKey publicKey)
			throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(publicKey
				.getEncoded());
		SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(
				(ASN1Sequence) new ASN1InputStream(bais).readObject());
		return new SubjectKeyIdentifier(info);
	}

	private static AuthorityKeyIdentifier createAuthorityKeyId(
			PublicKey publicKey) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(publicKey
				.getEncoded());
		SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(
				(ASN1Sequence) new ASN1InputStream(bais).readObject());
		return new AuthorityKeyIdentifier(info);
	}
}
