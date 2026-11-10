package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SigningKeyTests {

	private final JwtConfig config = new JwtConfig();

	static String pem(KeyPair keyPair) {
		return "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
	}

	@Test
	void usesTheConfiguredPrivateKeyAndDerivesItsPublicKey() {
		KeyPair configured = JwtConfig.generateRsaKeyPair();

		KeyPair used = config.jwtSigningKeyPair(pem(configured));

		assertThat(used.getPrivate()).isEqualTo(configured.getPrivate());
		assertThat(used.getPublic()).isEqualTo(configured.getPublic());
	}

	@Test
	void generatesAKeyWhenNoneIsConfigured() {
		KeyPair first = config.jwtSigningKeyPair("");
		KeyPair second = config.jwtSigningKeyPair(" ");

		assertThat(first.getPublic()).isNotEqualTo(second.getPublic());
	}

	@Test
	void refusesToStartWithAKeyThatIsNotAPkcs8RsaPrivateKey() {
		assertThatThrownBy(() -> config.jwtSigningKeyPair("not a key")).isInstanceOf(IllegalStateException.class)
			.hasMessage("inventory.jwt.private-key must be a PEM-encoded PKCS#8 RSA private key");
	}

}
