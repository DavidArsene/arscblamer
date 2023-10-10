/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devrel.gmscore.tools.common

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Utilities for working with apk files. */
object ApkUtils {

    /** Returns true if there exists a file whose name matches `filename` in `apkFile`. */
    @Throws(IOException::class)
    fun hasFile(apkFile: File, filename: String): Boolean {
        ZipFile(apkFile).use { apkZip -> return apkZip.getEntry(filename) != null }
    }

    /**
     * Returns a file whose name matches `filename`, or null if no file was found.
     *
     * @param apkFile The file containing the apk zip archive.
     * @param filename The full filename (e.g. res/raw/foo.bar).
     * @return A byte array containing the contents of the matching file, or null if not found.
     * @throws IOException Thrown if there's a matching file, but it cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFile(apkFile: File, filename: String): ByteArray? {
        ZipFile(apkFile).use { apkZip ->
            val zipEntry = apkZip.getEntry(filename) ?: return null
            apkZip.getInputStream(zipEntry).use { return it.readAllBytes() }
        }
    }

    /**
     * Returns a file whose name matches `filename`, or null if no file was found.
     *
     * @param apkFile The [FileSystem] representation of the apk zip archive.
     * @param filename The full filename (e.g. res/raw/foo.bar).
     * @return A byte array containing the contents of the matching file, or null if not found.
     * @throws IOException Thrown if there's a matching file, but it cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFile(apkFile: FileSystem, filename: String?): ByteArray? {
        return Files.readAllBytes(apkFile.getPath("/", filename))
    }

    /**
     * Returns a file whose name matches `filename`, or null if no file was found.
     *
     * @param inputStream The input stream containing the apk zip archive.
     * @param filename The full filename (e.g. res/raw/foo.bar).
     * @return A byte array containing the contents of the matching file, or null if not found.
     * @throws IOException Thrown if there's a matching file, but it cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFile(inputStream: InputStream, filename: String): ByteArray? {
        val files = getFiles(inputStream, Pattern.compile(Pattern.quote(filename)))
        return files[filename]
    }

    /**
     * Returns all files in an apk that match a given regular expression.
     *
     * @param apkFile The file containing the apk zip archive.
     * @param regex A regular expression to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFiles(apkFile: File, regex: String): Map<String, ByteArray> {
        return getFiles(apkFile, Pattern.compile(regex))
    }

    /**
     * Returns all files in an apk that match a given regular expression.
     *
     * @param apkFile The file containing the apk zip archive.
     * @param regex A regular expression to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFiles(apkFile: File, regex: Pattern): Map<String, ByteArray> {
        ZipFile(apkFile).use { apkZip ->
            return LinkedHashMap<String, ByteArray>().apply {
                for (entry in apkZip.entries()) {
                    if (regex.matcher(entry.name).matches()) {
                        apkZip.getInputStream(entry).use {
                            put(entry.name, it.readAllBytes())
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns all files in an apk that match a given regular expression.
     *
     * @param apkFile The [FileSystem] representation of the apk zip archive.
     * @param matcher A [PathMatcher] to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun getFiles(apkFile: FileSystem, matcher: PathMatcher): Map<String, ByteArray> {
        return HashMap<String, ByteArray>().apply {
            for (path in findFiles(apkFile, matcher)) {
                put(path.toString(), Files.readAllBytes(path))
            }
        }
    }

    /**
     * Finds all files in an apk that match a given regular expression.
     *
     * @param apkFile The [FileSystem] representation of the apk zip archive.
     * @param matcher A [PathMatcher] to match the requested filenames.
     * @return A list of paths matching the provided matcher.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    @Throws(IOException::class)
    fun findFiles(apkFile: FileSystem, matcher: PathMatcher): List<Path> {
        val result = ArrayList<Path>()
        val root = apkFile.getPath("/")
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(p: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (matcher.matches(p) || matcher.matches(p.normalize())) {
                    result.add(
                        root.relativize(p) // fancy way of eliding leading slash
                    )
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, e: IOException) = FileVisitResult.SKIP_SUBTREE
        })
        return result
    }

    /** Reads all files from an input stream that is reading from a zip file. */
    @Throws(IOException::class)
    fun getFiles(inputStream: InputStream, regex: Pattern): Map<String, ByteArray> {
        return LinkedHashMap<String, ByteArray>().apply {
            ZipInputStream(inputStream).use { zipInputStream ->
                BufferedInputStream(zipInputStream).use { bis ->
                    while (true) {
                        val entry = zipInputStream.nextEntry ?: break
                        if (regex.matcher(entry.name).matches()) {
                            put(entry.name, bis.readAllBytes())
                        }
                        zipInputStream.closeEntry()
                    }
                }
            }
        }
    }
}
