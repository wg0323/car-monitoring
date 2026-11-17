package com.cm.transport;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 809 链路会话：同一平台再次登录关闭旧连接、旧连接迟到关闭不清新链路、
 * 发送优先主链路且未登录不发送。
 */
class Jt809LinkSessionTest {

    private static final long CENTER_ID = 4000001L;

    private static final long WRITE_TIMEOUT_MILLIS = 500L;

    @Test
    @DisplayName("同一平台再次绑定主链路时关闭旧连接")
    void should_close_previous_main_channel_on_rebind() {
        Jt809LinkSession session = new Jt809LinkSession(CENTER_ID);
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        session.bindMain(oldChannel);

        EmbeddedChannel newChannel = new EmbeddedChannel();
        session.bindMain(newChannel);

        assertFalse(oldChannel.isActive(), "旧主链路应被关闭");
        assertTrue(session.isMainActive());
    }

    @Test
    @DisplayName("旧连接迟到关闭不清掉新登记的主链路")
    void should_keep_new_main_link_when_old_channel_unbinds_late() {
        Jt809LinkSession session = new Jt809LinkSession(CENTER_ID);
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        session.bindMain(oldChannel);
        session.bindMain(new EmbeddedChannel());
        session.markMainLoggedIn(true);

        session.unbind(oldChannel);

        assertTrue(session.isMainActive());
        assertTrue(session.isMainLoggedIn());
    }

    @Test
    @DisplayName("链路未登录时不可用，标记登录后才可发送")
    void should_require_login_before_link_is_usable() {
        Jt809LinkSession session = new Jt809LinkSession(CENTER_ID);
        session.bindSub(new EmbeddedChannel());

        assertFalse(session.hasUsableLink());

        session.markSubLoggedIn(true);

        assertTrue(session.isSubLoggedIn());
        assertTrue(session.hasUsableLink());
    }

    @Test
    @DisplayName("主链路未登录时写入回退到从链路")
    void should_fall_back_to_sub_link_when_main_not_logged_in() {
        Jt809LinkSession session = new Jt809LinkSession(CENTER_ID);
        EmbeddedChannel mainChannel = new EmbeddedChannel();
        EmbeddedChannel subChannel = new EmbeddedChannel();
        session.bindMain(mainChannel);
        session.bindSub(subChannel);
        session.markSubLoggedIn(true);

        assertTrue(session.writeAny(new byte[] {0x01}, WRITE_TIMEOUT_MILLIS));

        assertNotNull(subChannel.readOutbound(), "写入应落在从链路");
        assertNull(mainChannel.readOutbound(), "主链路未登录不应收到报文");
    }

    @Test
    @DisplayName("主链路登录后发送优先走主链路")
    void should_prefer_main_link_when_logged_in() {
        Jt809LinkSession session = new Jt809LinkSession(CENTER_ID);
        EmbeddedChannel mainChannel = new EmbeddedChannel();
        EmbeddedChannel subChannel = new EmbeddedChannel();
        session.bindMain(mainChannel);
        session.bindSub(subChannel);
        session.markMainLoggedIn(true);
        session.markSubLoggedIn(true);

        assertTrue(session.writeAny(new byte[] {0x02}, WRITE_TIMEOUT_MILLIS));

        assertNotNull(mainChannel.readOutbound(), "写入应落在主链路");
        assertNull(subChannel.readOutbound(), "主链路可用时不应走从链路");
    }
}
