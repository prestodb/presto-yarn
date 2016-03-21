package com.teradata.presto.yarn.test.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to help digest (calculate md5sum etc.) files data.
 */
public class FileDigesters
{
    public static String md5sum(Path path)
    {
        MessageDigest messageDigest = getMd5();
        byte[] buffer = new byte[4096];
        try (InputStream is = new FileInputStream(path.toFile())) {
            int read;
            while ((read = is.read(buffer)) > 0) {
                messageDigest.update(buffer, 0, read);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return String.format("%032x", new BigInteger(1, messageDigest.digest()));
    }

    private static MessageDigest getMd5()
    {
        try {
            return MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
