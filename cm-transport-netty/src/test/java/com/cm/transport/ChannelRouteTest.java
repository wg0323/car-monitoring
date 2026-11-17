package com.cm.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelRouteTest {

    @Test
    void should_reject_stale_route_when_old_connection_closes_late() {
        ChannelRoute oldRoute = new ChannelRoute("13912345678", "gateway-1", "channel-1", 1L);
        ChannelRoute newRoute = new ChannelRoute("13912345678", "gateway-1", "channel-2", 2L);

        assertFalse(oldRoute.sameConnection(newRoute));
        assertFalse(oldRoute.sameConnection(new ChannelRoute("13900000000", "gateway-1", "channel-1", 1L)));
        assertFalse(oldRoute.sameConnection(null));
    }

    @Test
    void should_accept_route_when_incarnation_and_channel_match() {
        ChannelRoute route = new ChannelRoute("13912345678", "gateway-1", "channel-1", 1L);
        ChannelRoute same = new ChannelRoute("13912345678", "gateway-1", "channel-1", 1L);

        assertTrue(route.sameConnection(same));
    }
}
