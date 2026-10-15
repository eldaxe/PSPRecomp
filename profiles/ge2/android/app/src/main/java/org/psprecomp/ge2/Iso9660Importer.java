package org.psprecomp.ge2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small, bounded ISO-9660 reader for PSP disc trees. It intentionally does not handle CSO. */
final class Iso9660Importer {
    private static final int SECTOR = 2048;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ENTRIES = 250_000;

    private Iso9660Importer() {}

    static void extract(File iso, File output) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(iso, "r")) {
            long rootExtent = -1;
            long rootLength = -1;
            byte[] sector = new byte[SECTOR];
            for (int i = 16; i < 80; i++) {
                long offset = (long) i * SECTOR;
                if (offset + SECTOR > in.length()) break;
                in.seek(offset);
                in.readFully(sector);
                int type = sector[0] & 0xff;
                if (!ascii(sector, 1, 5).equals("CD001") || (sector[6] & 0xff) != 1)
                    throw new IOException("ISO 9660 volume descriptor inválido.");
                if (type == 255) break;
                if (type == 1) {
                    int record = 156;
                    if ((sector[record] & 0xff) < 34) throw new IOException("Root directory record inválido.");
                    rootExtent = u32le(sector, record + 2);
                    rootLength = u32le(sector, record + 10);
                    break;
                }
            }
            if (rootExtent < 0 || rootLength <= 0) throw new IOException("Primary Volume Descriptor tidak ditemukan.");
            Counter count = new Counter();
            extractDirectory(in, rootExtent, rootLength, output, 0, count);
        }
    }

    private static void extractDirectory(RandomAccessFile in, long extent, long length, File target,
                                         int depth, Counter count) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Kedalaman direktori ISO melebihi batas.");
        if (++count.entries > MAX_ENTRIES) throw new IOException("Jumlah entri ISO melebihi batas.");
        if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory())
            throw new IOException("Tidak dapat membuat direktori: " + target);
        long start = extent * SECTOR;
        long end = start + length;
        if (start < 0 || end > in.length() || end < start) throw new IOException("Rentang direktori di luar file ISO.");
        long pos = start;
        while (pos < end) {
            in.seek(pos);
            int recordLength = in.read();
            if (recordLength < 0) throw new IOException("ISO terpotong.");
            if (recordLength == 0) {
                pos = ((pos / SECTOR) + 1) * SECTOR;
                continue;
            }
            if (recordLength < 34 || pos + recordLength > end)
                throw new IOException("Directory record ISO tidak valid.");
            byte[] record = new byte[recordLength];
            in.seek(pos);
            in.readFully(record);
            int nameLength = record[32] & 0xff;
            if (33 + nameLength > record.length) throw new IOException("Nama entri ISO tidak valid.");
            String name;
            if (nameLength == 1 && (record[33] == 0 || record[33] == 1)) {
                pos += recordLength;
                continue;
            }
            name = new String(record, 33, nameLength, StandardCharsets.US_ASCII);
            name = normalizeName(name);
            long childExtent = u32le(record, 2);
            long childLength = u32le(record, 10);
            boolean directory = (record[25] & 0x02) != 0;
            if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
                throw new IOException("Nama entri ISO tidak aman.");
            File child = new File(target, name);
            if (!child.getCanonicalPath().startsWith(target.getCanonicalPath() + File.separator))
                throw new IOException("Path entri ISO keluar dari direktori impor.");
            if (directory) {
                extractDirectory(in, childExtent, childLength, child, depth + 1, count);
            } else {
                if (++count.entries > MAX_ENTRIES) throw new IOException("Jumlah entri ISO melebihi batas.");
                copyExtent(in, childExtent, childLength, child);
            }
            pos += recordLength;
        }
    }

    private static void copyExtent(RandomAccessFile in, long extent, long length, File target) throws IOException {
        long start = extent * SECTOR;
        long end = start + length;
        if (length < 0 || start < 0 || end > in.length() || end < start)
            throw new IOException("Rentang file ISO tidak valid.");
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
            throw new IOException("Tidak dapat membuat direktori tujuan.");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            in.seek(start);
            long remaining = length;
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int n = in.read(buffer, 0, want);
                if (n < 0) throw new IOException("File ISO terpotong.");
                out.write(buffer, 0, n);
                remaining -= n;
            }
        }
    }

    private static String normalizeName(String raw) {
        String name = raw;
        int version = name.lastIndexOf(';');
        if (version >= 0) name = name.substring(0, version);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        return name.toUpperCase(Locale.ROOT);
    }

    private static String ascii(byte[] b, int off, int len) {
        return new String(b, off, len, StandardCharsets.US_ASCII);
    }

    private static long u32le(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    private static final class Counter { int entries; }
}
