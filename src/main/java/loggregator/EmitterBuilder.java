/*
 *   Copyright (c) 2013 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package loggregator;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.UUID;

/**
 * Builder class for creating instances of {@link loggregator.Emitter}
 *
 * @author Mike Heath <elcapo@gmail.com>
 */
public class EmitterBuilder {

	public static final int MAX_MESSAGE_BYTE_SIZE = (8 * 1024) - 512;
    private static final ByteString TRUNCATED_STRING = ByteString.copyFromUtf8("TRUNCATED");

	private final InetSocketAddress address;
	private final String secret;


	private boolean blocking = false;
	private String sourceName = "UNKNOWN";
	private String sourceId = "0";

	/**
	 * Creates a new {@code EmitterBuilder} for emitting messages to the provided host.
	 *
	 * @param host the Loggregator host that events are sent to
	 * @param port the port the Loggregator host is listening for events on
	 * @param secret the shared secret used for authenticating logging events
	 */
	public EmitterBuilder(String host, int port, String secret) {
		this(new InetSocketAddress(host, port), secret);
	}

	/**
	 * Creates a new {@code EmitterBuilder} for emitting messages to the provided host.
	 *
	 * @param address the socket adddress of the Loggregator host that events are sent to
	 * @param secret the shared secret used for authenticating logging events
	 */
	public EmitterBuilder(InetSocketAddress address, String secret) {
		this.address = address;
		this.secret = secret;
	}

	/**
	 * Indicates whether the emitter should wait until the logging event has been sent over the network or not.
	 *
	 * @param blocking {@code true} if the emitter should block until the logging event has been sent, {@code false} if
	 *                             the client should not wait. The default is {@code false}.
	 * @return this builder instance
	 */
	public EmitterBuilder blocking(boolean blocking) {
		this.blocking = blocking;
		return this;
	}

	/**
	 * The source name attached to logging events.
	 *
	 * @param sourceName  The name of the source attached to logging events.
	 * @return this builder instance
	 */
	public EmitterBuilder sourceName(String sourceName) {
		this.sourceName = sourceName;
		return this;
	}

	/**
	 * The source's instance id.
	 *
	 * @param sourceId the instance id of the source.
	 * @return this builder instance
	 */
	public EmitterBuilder sourceId(String sourceId) {
		this.sourceId = sourceId;
		return this;
	}

	public Emitter build() {
		return new DefaultEmitter(this);
	}

	private static class DefaultEmitter implements Emitter {

		private final DatagramChannel channel;

		private final MessageSigner signer;

		private final String sourceName;
		private final String sourceId;

		public DefaultEmitter(EmitterBuilder builder) {
			try {
				channel = DatagramChannel.open();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				channel.connect(builder.address);
				channel.configureBlocking(builder.blocking);
			} catch (IOException e) {
				try {
					channel.close();
				} catch (IOException e1) {
					// Ignore any error closing the channel.
				}
				throw new RuntimeException(e);
			}
			signer = new MessageSigner(builder.secret);
			this.sourceName = builder.sourceName;
			this.sourceId = builder.sourceId;
		}

		@Override
		public void emit(String appId, String message) {
			sendMessage(appId, message, Messages.LogMessage.MessageType.OUT);
		}

		@Override
		public void emit(UUID appId, String message) {
			sendMessage(appId.toString(), message, Messages.LogMessage.MessageType.OUT);
		}

		@Override
		public void emitError(String appId, String message) {
			sendMessage(appId, message, Messages.LogMessage.MessageType.ERR);
		}

		@Override
		public void emitError(UUID appId, String message) {
			sendMessage(appId.toString(), message, Messages.LogMessage.MessageType.ERR);
		}

		@Override
		public void close() {
			try {
				channel.close();
			} catch (IOException e) {
				throw new LoggregatorException(e);
			}
		}

		private void sendMessage(String appId, String message, Messages.LogMessage.MessageType type) {
			final Messages.LogMessage logMessage = buildLogMessage(appId, message, type);
			final Messages.LogEnvelope envelope = Messages.LogEnvelope.newBuilder()
					.setRoutingKey(appId)
					.setLogMessage(logMessage)
					.setSignature(ByteString.copyFrom(signer.sign(logMessage)))
					.build();
			writeMessage(envelope);
		}

		private Messages.LogMessage buildLogMessage(String appId, String message, Messages.LogMessage.MessageType type) {
			ByteString messageBytes = ByteString.copyFromUtf8(message);
			if (messageBytes.size() > MAX_MESSAGE_BYTE_SIZE) {
				messageBytes = messageBytes.substring(0, MAX_MESSAGE_BYTE_SIZE - TRUNCATED_STRING.size()).concat(TRUNCATED_STRING);
			}
			final Messages.LogMessage.Builder builder = Messages.LogMessage.newBuilder()
					.setAppId(appId)
					.setMessage(messageBytes)
					.setMessageType(type)
					.setTimestamp(System.currentTimeMillis() * 1000000);

			if (sourceId != null) {
				builder.setSourceId(sourceId);
			}
			if (sourceName != null) {
				builder.setSourceName(sourceName);
			}
			return builder.build();
		}

		private void writeMessage(MessageLite message) {
			final byte[] bytes = message.toByteArray();
			final ByteBuffer buffer = ByteBuffer.wrap(bytes);
			try {
				channel.write(buffer);
			} catch (IOException e) {
				throw new LoggregatorException(e);
			}
		}
	}
}
