package com.capstone.etp.mcp;

/** What an AI assistant connector may do. READ is always granted; the others are chosen when the link is created. */
public enum McpScope {
    READ,
    ALERTS,
    TRADE
}
