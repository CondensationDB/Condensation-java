package condensation.actors;

public class EntrustedActor {
	public final PublicKey publicKey;
	public final String storeUrl;

	public EntrustedActor(PublicKey publicKey, String storeUrl) {
		this.publicKey = publicKey;
		this.storeUrl = storeUrl;
	}
}
