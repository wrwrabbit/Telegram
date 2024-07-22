package org.telegram.messenger.partisan.appmigration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidManifestExtractor {
    public static int endDocTag = 0x00100101;
    public static int startTag = 0x00100102;
    public static int endTag = 0x00100103;

    public static String extractPackageNameFromApk(File apkFile) {
        try {
            JarFile jarFile = new JarFile(apkFile);
            InputStream is = jarFile.getInputStream(jarFile.getEntry("AndroidManifest.xml"));
            byte[] compressedXml = new byte[is.available()];
            is.read(compressedXml);
            String decompressed = AndroidManifestExtractor.decompressXML(compressedXml);
            Pattern pattern = Pattern.compile("package=\"(([A-Za-z]{1}[A-Za-z\\d_]*\\.)+[A-Za-z][A-Za-z\\d_]*)\"");
            Matcher matcher = pattern.matcher(decompressed);
            if (matcher.find()){
                return matcher.group(1);
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    public static String decompressXML(byte[] xml) {
        StringBuilder finalXML = new StringBuilder();
        int numbStrings = LEW(xml, 4 * 4);
        int sitOff = 0x24;
        int stOff = sitOff + numbStrings * 4;
        int xmlTagOff = LEW(xml, 3 * 4);
        for (int ii = xmlTagOff; ii < xml.length - 4; ii += 4) {
            if (LEW(xml, ii) == startTag) {
                xmlTagOff = ii;
                break;
            }
        }
        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while (off < xml.length) {
            int tag0 = LEW(xml, off);
            int lineNo = LEW(xml, off + 2 * 4);
            int nameNsSi = LEW(xml, off + 4 * 4);
            int nameSi = LEW(xml, off + 5 * 4);

            if (tag0 == startTag) {
                int tag6 = LEW(xml, off + 6 * 4);
                int numbAttrs = LEW(xml, off + 7 * 4);
                off += 9 * 4;
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                startTagLineNo = lineNo;

                StringBuffer sb = new StringBuffer();
                for (int ii = 0; ii < numbAttrs; ii++) {
                    int attrNameNsSi = LEW(xml, off);
                    int attrNameSi = LEW(xml, off + 1 * 4);
                    int attrValueSi = LEW(xml, off + 2 * 4);
                    int attrFlags = LEW(xml, off + 3 * 4);
                    int attrResId = LEW(xml, off + 4 * 4);
                    off += 5 * 4;

                    String attrName = compXmlString(xml, sitOff, stOff,
                            attrNameSi);
                    String attrValue = attrValueSi != -1 ? compXmlString(xml,
                            sitOff, stOff, attrValueSi) : "resourceID 0x"
                            + Integer.toHexString(attrResId);
                    sb.append(" " + attrName + "=\"" + attrValue + "\"");
                }
                finalXML.append("<" + name + sb + ">");
                indent++;

            } else if (tag0 == endTag) {
                indent--;
                off += 6 * 4;
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                finalXML.append("</" + name + ">");
            } else if (tag0 == endDocTag) {
                break;
            } else {
                break;
            }
        }
        return finalXML.toString();
    }

    public static String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
        if (strInd < 0)
            return null;
        int strOff = stOff + LEW(xml, sitOff + strInd * 4);
        return compXmlStringAt(xml, strOff);
    }

    public static String compXmlStringAt(byte[] arr, int strOff) {
        int strLen = arr[strOff + 1] << 8 & 0xff00 | arr[strOff] & 0xff;
        byte[] chars = new byte[strLen];
        for (int ii = 0; ii < strLen; ii++) {
            chars[ii] = arr[strOff + 2 + ii * 2];
        }
        return new String(chars);
    }

    public static int LEW(byte[] arr, int off) {
        return arr[off + 3] << 24 & 0xff000000 | arr[off + 2] << 16 & 0xff0000
                | arr[off + 1] << 8 & 0xff00 | arr[off] & 0xFF;
    }
}