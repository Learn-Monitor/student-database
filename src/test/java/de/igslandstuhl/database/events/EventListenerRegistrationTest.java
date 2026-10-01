package de.igslandstuhl.database.events;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class EventListenerRegistrationTest {
    @Test
    void registeringPluginEventListenerInitializesItsEventBucket() {
        AtomicBoolean called = new AtomicBoolean();
        new EventListener<TestEvent>(ListenerPriority.LOW) {
            @Override
            public void onEvent(TestEvent event) {
                called.set(true);
            }

            @Override
            public EventType<TestEvent> getEventType() {
                return TestEvent.TYPE;
            }
        }.register();

        EventListener.fireEvent(new TestEvent());

        assertTrue(called.get());
    }

    private static final class TestEvent extends Event {
        private static final EventType<TestEvent> TYPE = new EventType<>("EventListenerRegistrationTest");

        @Override
        public EventType<TestEvent> getType() {
            return TYPE;
        }
    }
}
