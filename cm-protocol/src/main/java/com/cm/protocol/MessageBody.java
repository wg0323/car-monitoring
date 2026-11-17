package com.cm.protocol;

/**
 * 消息体解码结果。只承载解码后的业务语义，不负责下行编码。
 */
public interface MessageBody {

    int messageId();
}
