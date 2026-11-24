package com.enjoytrip;

/** Identity taken from a verified access token. credentialVersion is re-checked against the DB. */
public record AuthPrincipal(String id, long credentialVersion) {}
