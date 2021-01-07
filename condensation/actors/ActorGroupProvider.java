package condensation.actors;

public interface ActorGroupProvider {
	void updateActorGroup(ActorGroupProvider.Done done);

	void updateEntrustedKeys(ActorGroupProvider.Done done);

	interface Done {
		void onActorGroupReady();

		void onActorGroupNotReady();
	}

	interface EntrustedActorsDone {
		void onEntrustedActorsReady();
	}
}
