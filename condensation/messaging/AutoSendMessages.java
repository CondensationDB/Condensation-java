package condensation.messaging;

import condensation.Condensation;
import condensation.tasks.LongLazyActionWithExponentialBackoff;
import condensation.unionList.ChangeListener;

public class AutoSendMessages extends LongLazyActionWithExponentialBackoff implements ChangeListener {
	final MessagingStore messagingStore;

	public AutoSendMessages(MessagingStore messagingStore) {
		super(100, 2f, Condensation.DAY);
		this.messagingStore = messagingStore;

		messagingStore.sentList.changeListeners.add(this);
	}

	@Override
	protected void action(Action action) {
		new Save(action);
	}

	@Override
	public void onUnionListChanged() {
		schedule();
	}

	class Save implements MessagingStore.SendMessagesDone {
		final Action action;

		Save(Action action) {
			this.action = action;
			messagingStore.sendMessages(this);
		}

		@Override
		public void onSendMessagesDone() {
			action.done();
		}

		@Override
		public void onSendMessagesFailed() {
			Condensation.log("Sending messages failed");
			action.failed();
		}
	}
}
