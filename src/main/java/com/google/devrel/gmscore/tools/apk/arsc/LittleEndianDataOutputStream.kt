package com.google.devrel.gmscore.tools.apk.arsc

import java.io.DataOutput
import java.io.DataOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

class LittleEndianDataOutputStream(out: OutputStream?)
    : FilterOutputStream(DataOutputStream(out)), DataOutput {

    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

    override fun writeShort(i: Int) {
        out.write(0xFF and i)
        out.write(0xFF and (i shr 8))
    }

    override fun writeInt(i: Int) {
        out.write(0xFF and i)
        out.write(0xFF and (i shr 8))
        out.write(0xFF and (i shr 16))
        out.write(0xFF and (i shr 24))
    }

    override fun writeBoolean(b: Boolean) {}

    override fun writeByte(i: Int) {}

    override fun writeChar(i: Int) {}

    override fun writeLong(l: Long) {}

    override fun writeFloat(f: Float) {}

    override fun writeDouble(d: Double) {}

    override fun writeBytes(s: String) {}

    override fun writeChars(s: String) {}

    override fun writeUTF(s: String) {}
}