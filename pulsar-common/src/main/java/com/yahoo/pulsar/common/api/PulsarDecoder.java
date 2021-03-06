/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.pulsar.common.api;

import static com.google.common.base.Preconditions.checkArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.pulsar.common.api.proto.PulsarApi.BaseCommand;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandAck;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandCloseConsumer;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandCloseProducer;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandConnect;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandConnected;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandError;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandFlow;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandMessage;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandPing;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandPong;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandProducer;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandProducerSuccess;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandRedeliverUnacknowledgedMessages;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandSend;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandSendError;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandSendReceipt;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandSubscribe;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandSuccess;
import com.yahoo.pulsar.common.api.proto.PulsarApi.CommandUnsubscribe;
import com.yahoo.pulsar.common.util.protobuf.ByteBufCodedInputStream;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public abstract class PulsarDecoder extends ChannelInboundHandlerAdapter {

    // Max message size is limited by max BookKeeper entry size which is 5MB, and we need to account
    // for headers as well.
    public final static int MaxMessageSize = (5 * 1024 * 1024 - (10 * 1024));
    public final static int MaxFrameSize = 5 * 1024 * 1024;

    @Override
    final public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Get a buffer that contains the full frame
        ByteBuf buffer = (ByteBuf) msg;
        BaseCommand cmd = null;
        BaseCommand.Builder cmdBuilder = null;

        try {
            // De-serialize the command
            int cmdSize = (int) buffer.readUnsignedInt();
            int writerIndex = buffer.writerIndex();
            buffer.writerIndex(buffer.readerIndex() + cmdSize);
            ByteBufCodedInputStream cmdInputStream = ByteBufCodedInputStream.get(buffer);
            cmdBuilder = BaseCommand.newBuilder();
            cmd = cmdBuilder.mergeFrom(cmdInputStream, null).build();
            buffer.writerIndex(writerIndex);

            cmdInputStream.recycle();

            if (log.isDebugEnabled()) {
                log.debug("[{}] Received cmd {}", ctx.channel().remoteAddress(), cmd.getType());
            }

            messageReceived();

            switch (cmd.getType()) {
            case ACK:
                checkArgument(cmd.hasAck());
                handleAck(cmd.getAck());
                cmd.getAck().getMessageId().recycle();
                cmd.getAck().recycle();
                break;

            case CLOSE_CONSUMER:
                checkArgument(cmd.hasCloseConsumer());
                handleCloseConsumer(cmd.getCloseConsumer());
                cmd.getCloseConsumer().recycle();
                break;

            case CLOSE_PRODUCER:
                checkArgument(cmd.hasCloseProducer());
                handleCloseProducer(cmd.getCloseProducer());
                cmd.getCloseProducer().recycle();
                break;

            case CONNECT:
                checkArgument(cmd.hasConnect());
                handleConnect(cmd.getConnect());
                cmd.getConnect().recycle();
                break;
            case CONNECTED:
                checkArgument(cmd.hasConnected());
                handleConnected(cmd.getConnected());
                cmd.getConnected().recycle();
                break;

            case ERROR:
                checkArgument(cmd.hasError());
                handleError(cmd.getError());
                cmd.getError().recycle();
                break;

            case FLOW:
                checkArgument(cmd.hasFlow());
                handleFlow(cmd.getFlow());
                cmd.getFlow().recycle();
                break;

            case MESSAGE: {
                checkArgument(cmd.hasMessage());
                handleMessage(cmd.getMessage(), buffer);
                cmd.getMessage().recycle();
                break;
            }
            case PRODUCER:
                checkArgument(cmd.hasProducer());
                handleProducer(cmd.getProducer());
                cmd.getProducer().recycle();
                break;

            case SEND: {
                checkArgument(cmd.hasSend());

                // Store a buffer marking the content + headers
                ByteBuf headersAndPayload = buffer.markReaderIndex();
                handleSend(cmd.getSend(), headersAndPayload);
                cmd.getSend().recycle();
                break;
            }
            case SEND_ERROR:
                checkArgument(cmd.hasSendError());
                handleSendError(cmd.getSendError());
                cmd.getSendError().recycle();
                break;

            case SEND_RECEIPT:
                checkArgument(cmd.hasSendReceipt());
                handleSendReceipt(cmd.getSendReceipt());
                cmd.getSendReceipt().recycle();
                break;

            case SUBSCRIBE:
                checkArgument(cmd.hasSubscribe());
                handleSubscribe(cmd.getSubscribe());
                cmd.getSubscribe().recycle();
                break;

            case SUCCESS:
                checkArgument(cmd.hasSuccess());
                handleSuccess(cmd.getSuccess());
                cmd.getSuccess().recycle();
                break;

            case PRODUCER_SUCCESS:
                checkArgument(cmd.hasProducerSuccess());
                handleProducerSuccess(cmd.getProducerSuccess());
                cmd.getProducerSuccess().recycle();
                break;

            case UNSUBSCRIBE:
                checkArgument(cmd.hasUnsubscribe());
                handleUnsubscribe(cmd.getUnsubscribe());
                cmd.getUnsubscribe().recycle();
                break;

            case PING:
                checkArgument(cmd.hasPing());
                handlePing(cmd.getPing());
                cmd.getPing().recycle();
                break;

            case PONG:
                checkArgument(cmd.hasPong());
                handlePong(cmd.getPong());
                cmd.getPong().recycle();
                break;

            case REDELIVER_UNACKNOWLEDGED_MESSAGES:
                checkArgument(cmd.hasRedeliverUnacknowledgedMessages());
                handleRedeliverUnacknowledged(cmd.getRedeliverUnacknowledgedMessages());
                cmd.getRedeliverUnacknowledgedMessages().recycle();
                break;
            }

        } finally {
            if (cmdBuilder != null) {
                cmdBuilder.recycle();
            }

            if (cmd != null) {
                cmd.recycle();
            }

            buffer.release();
        }
    }

    protected abstract void messageReceived();

    protected void handleConnect(CommandConnect connect) {
        throw new UnsupportedOperationException();
    }

    protected void handleConnected(CommandConnected connected) {
        throw new UnsupportedOperationException();
    }

    protected void handleSubscribe(CommandSubscribe subscribe) {
        throw new UnsupportedOperationException();
    }

    protected void handleProducer(CommandProducer producer) {
        throw new UnsupportedOperationException();
    }

    protected void handleSend(CommandSend send, ByteBuf headersAndPayload) {
        throw new UnsupportedOperationException();
    }

    protected void handleSendReceipt(CommandSendReceipt sendReceipt) {
        throw new UnsupportedOperationException();
    }

    protected void handleSendError(CommandSendError sendError) {
        throw new UnsupportedOperationException();
    }

    protected void handleMessage(CommandMessage cmdMessage, ByteBuf headersAndPayload) {
        throw new UnsupportedOperationException();
    }

    protected void handleAck(CommandAck ack) {
        throw new UnsupportedOperationException();
    }

    protected void handleFlow(CommandFlow flow) {
        throw new UnsupportedOperationException();
    }

    protected void handleRedeliverUnacknowledged(CommandRedeliverUnacknowledgedMessages redeliver) {
        throw new UnsupportedOperationException();
    }

    protected void handleUnsubscribe(CommandUnsubscribe unsubscribe) {
        throw new UnsupportedOperationException();
    }

    protected void handleSuccess(CommandSuccess success) {
        throw new UnsupportedOperationException();
    }

    protected void handleProducerSuccess(CommandProducerSuccess success) {
        throw new UnsupportedOperationException();
    }

    protected void handleError(CommandError error) {
        throw new UnsupportedOperationException();
    }

    protected void handleCloseProducer(CommandCloseProducer closeProducer) {
        throw new UnsupportedOperationException();
    }

    protected void handleCloseConsumer(CommandCloseConsumer closeConsumer) {
        throw new UnsupportedOperationException();
    }

    protected void handlePing(CommandPing ping) {
        throw new UnsupportedOperationException();
    }

    protected void handlePong(CommandPong pong) {
        throw new UnsupportedOperationException();
    }

    private static final Logger log = LoggerFactory.getLogger(PulsarDecoder.class);
}
