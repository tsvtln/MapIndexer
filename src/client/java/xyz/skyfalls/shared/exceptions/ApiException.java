package xyz.skyfalls.shared.exceptions;

import lombok.Getter;

@Getter
public class ApiException extends Exception {
	private final int code;
	private final String body;

	public ApiException(int code, String body) {
		super("Api returned non 2xx code: " + code + "->" + body);
		this.code = code;
		this.body = body;
	}

	@Override
	public String toString() {
		return body + " (api:" + code + ")";
	}
}
