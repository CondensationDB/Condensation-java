package condensation.actors;

import java.util.ArrayList;

public class EntrustedKeysList implements EntrustedKeysProvider {
	public final ArrayList<PublicKey> list = new ArrayList<>();

	public void add(PublicKey publicKey) {
		list.add(publicKey);
	}

	@Override
	public void getEntrustedKeys(Done done) {
		done.onGetEntrustedKeysDone(list);
	}
}
