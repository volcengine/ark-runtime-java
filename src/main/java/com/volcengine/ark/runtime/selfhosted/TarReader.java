// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class TarReader extends InputStream {
    private final InputStream in;
    private long remaining;
    private long entrySize;

    TarReader(InputStream in) {
        this.in = in;
    }

    Entry next() throws IOException {
        drainCurrent();
        byte[] header = new byte[512];
        int n = readFully(header);
        if (n <= 0 || isZeroBlock(header)) {
            return null;
        }
        String name = parseString(header, 0, 100);
        long size = parseOctal(header, 124, 12);
        byte type = header[156];
        remaining = size;
        entrySize = size;
        return new Entry(name, size, type == '5', type == 0 || type == '0');
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n < 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int n = in.read(b, off, (int) Math.min(len, remaining));
        if (n > 0) {
            remaining -= n;
        }
        return n;
    }

    private void drainCurrent() throws IOException {
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    break;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
        long padding = (512 - (entrySize % 512)) % 512;
        entrySize = 0;
        while (padding > 0) {
            long skipped = in.skip(padding);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    break;
                }
                skipped = 1;
            }
            padding -= skipped;
        }
    }

    private int readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        return off;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String parseString(byte[] block, int off, int len) {
        int end = off;
        while (end < off + len && block[end] != 0) {
            end++;
        }
        return new String(block, off, end - off, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] block, int off, int len) {
        long value = 0;
        for (int i = off; i < off + len; i++) {
            byte b = block[i];
            if (b < '0' || b > '7') {
                continue;
            }
            value = (value << 3) + (b - '0');
        }
        return value;
    }

    static class Entry {
        final String name;
        final long size;
        final boolean directory;
        final boolean regular;

        Entry(String name, long size, boolean directory, boolean regular) {
            this.name = name;
            this.size = size;
            this.directory = directory;
            this.regular = regular;
        }
    }
}
