package ch.batbern.agent.log;

/** Where an inbound message came from; the values of {@code agent_inbound_messages.channel}. */
public enum InboundChannel {
    MAIL,
    CHAT,
    CARD_ACTION
}
