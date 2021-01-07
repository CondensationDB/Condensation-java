package condensation.actors;

public interface EntrustedKeysProvider {
	void getEntrustedKeys(Done done);

	interface Done {
		void onGetEntrustedKeysDone(Iterable<PublicKey> entrustedKeys);

		void onGetEntrustedKeysFailed();
	}
}
