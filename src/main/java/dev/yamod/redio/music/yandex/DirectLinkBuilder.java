package dev.yamod.redio.music.yandex;

import dev.yamod.redio.music.MusicException;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Собирает прямую ссылку на незашифрованный mp3 из XML старого download-info.
 * Соль подписи — открытая константа клиентского протокола, не секрет пользователя.
 */
public final class DirectLinkBuilder {
    static final String SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA";

    private DirectLinkBuilder() {
    }

    public static String build(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new MusicException("XML download-info пустой.");
        }
        Document document = parse(xml);
        String host = text(document, "host");
        String path = text(document, "path");
        String ts = text(document, "ts");
        String s = text(document, "s");
        if (host.isEmpty() || path.isEmpty() || ts.isEmpty() || s.isEmpty()) {
            throw new MusicException("XML download-info не содержит host, path, ts и s.");
        }
        if (!host.matches("[A-Za-z0-9.-]+") || !path.startsWith("/") || path.contains("..") || path.chars().anyMatch(Character::isWhitespace)) {
            throw new MusicException("XML download-info содержит неожиданный адрес файла.");
        }
        String sign = md5(SIGN_SALT + path.substring(1) + s);
        return "https://" + host + "/get-mp3/" + sign + "/" + ts + path;
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception exception) {
            throw new MusicException("Не удалось разобрать XML download-info.");
        }
    }

    private static String text(Document document, String tag) {
        var nodes = document.getElementsByTagName(tag);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            throw new MusicException("Не удалось подписать ссылку на mp3.");
        }
    }
}
