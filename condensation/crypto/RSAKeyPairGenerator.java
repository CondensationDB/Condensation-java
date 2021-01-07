package condensation.crypto;

import java.math.BigInteger;

import condensation.Condensation;

// Adapted from BouncyCastle.org
public class RSAKeyPairGenerator {
	public static final BigInteger e = BigInteger.valueOf(65537);

	public static RSAPrivateKey generate() {
		while (true) {
			BigInteger e = RSAKeyPairGenerator.e;
			BigInteger p = randomPrime1024();

			// generate a modulus of the required length
			BigInteger q, n;
			while (true) {
				q = randomPrime1024();

				// Some implementations check if p - q > 2^800 (or a similar value), since pq
				// may be easy to factorize if p ~ q. However, the probability of this is less
				// than 2^-200, and therefore completely negligible.
				// For comparison, note that the Miller-Rabin primality test leaves a 2^-80
				// chance that either p or q are composite.

				// calculate the modulus
				n = p.multiply(q);
				if (n.bitLength() == 2048) break;

				// our primes aren't big enough, make the largest of the two p and try again
				p = p.max(q);
			}

			// Check if the NAF weight is high enough, since low-weight composites may be weak
			// See "The number field sieve for integers of low weight" by Oliver Schirokauer.
			BigInteger k3 = n.shiftLeft(1).add(n);
			BigInteger naf = k3.xor(n);
			if (naf.bitCount() < 512) continue;

			// Make p the bigger of the two primes
			if (p.compareTo(q) < 0) {
				BigInteger temp = p;
				p = q;
				q = temp;
			}

			// Create the private key
			return new RSAPrivateKey(e, p, q);
		}
	}

	// Returns a random prime p, with p - 1 relative prime to e
	private static BigInteger randomPrime1024() {
		while (true) {
			BigInteger p = new BigInteger(1024, 1, Condensation.secureRandom);
			if (p.mod(e).equals(BigInteger.ONE)) continue;
			if (!p.isProbablePrime(5)) continue;
			if (!e.gcd(p.subtract(BigInteger.ONE)).equals(BigInteger.ONE)) continue;
			return p;
		}
	}
}
