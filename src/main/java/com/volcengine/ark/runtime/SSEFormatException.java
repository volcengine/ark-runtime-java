// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime;

/**
 * Exception indicating a SSE format error
 */
public class SSEFormatException extends Throwable{
	public SSEFormatException(String msg){
		super(msg);
	}
}