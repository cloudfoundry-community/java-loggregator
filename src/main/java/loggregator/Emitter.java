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

import java.util.UUID;

/**
 * Client for emitting logging events to Loggregator.
 *
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface Emitter extends AutoCloseable {

	/**
	 * Emits a message to loggregator.
	 *
	 * @param appId  the guid of the application
	 * @param message the message to emit
	 */
	public void emit(String appId, String message);

	/**
	 * Emits a message to loggregator.
	 *
	 * @param appId  the guid of the application
	 * @param message the message to emit
	 */
	public void emit(UUID appId, String message);

	/**
	 * Emits an error message to loggregator.
	 *
	 * @param appId  the guid of the application
	 * @param message the message to emit
	 */
	public void emitError(String appId, String message);

	/**
	 * Emits an error message to loggregator.
	 *
	 * @param appId  the guid of the application
	 * @param message the message to emit
	 */
	public void emitError(UUID appId, String message);

}

