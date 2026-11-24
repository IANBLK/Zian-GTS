package com.zianblk.ziangts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.zianblk.ziangts.v2.client.UiText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LangFilesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[-#+0,(]*\\d*(\\.\\d+)?[sdfxXeEgG]");
    private static final Pattern FALLBACK = Pattern.compile("translatableWithFallback\\(\\s*\"([^\"]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static Map<String, String> load(String name) throws Exception {
        try (var stream = LangFilesTest.class.getResourceAsStream("/assets/ziangts/lang/" + name)) {
            var json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            var map = new TreeMap<String, String>();
            json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            return map;
        }
    }

    private static String placeholders(String text) {
        var found = new ArrayList<String>();
        var m = PLACEHOLDER.matcher(text);
        while (m.find()) found.add(m.group());
        Collections.sort(found);
        return String.join(",", found);
    }

    /** La carpeta de trabajo de las pruebas no es la del proyecto: se sube desde la carpeta de trabajo hasta encontrar src/main. */
    private static Path projectRoot() {
        var dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main"))) dir = dir.getParent();
        assertTrue(dir != null, "No se encontro la raiz del proyecto");
        return dir;
    }

    private static String unescape(String source) {
        return source.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    @Test void allLanguagesHaveSameKeysAndPlaceholders() throws Exception {
        var es = load("es_es.json");
        for (var other : new String[] {"en_us.json", "es_mx.json"}) {
            var map = load(other);
            assertEquals(es.keySet(), map.keySet(), "Claves distintas en " + other);
            es.forEach((key, text) -> assertEquals(placeholders(text), placeholders(map.get(key)), key + " en " + other));
        }
    }

    @Test void mexicanSpanishMatchesSpain() throws Exception {
        assertEquals(load("es_es.json"), load("es_mx.json"));
    }

    /** Cada translatableWithFallback del codigo tiene su clave y su respaldo es el texto de es_es (o el ingles original de los de administracion). */
    @Test void codeFallbacksMatchTheLanguageFiles() throws Exception {
        var es = load("es_es.json");
        var en = load("en_us.json");
        int checked = 0;
        try (Stream<Path> files = Files.walk(projectRoot().resolve("src/main"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".kt") || p.toString().endsWith(".java")).toList()) {
                var m = FALLBACK.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    var key = m.group(1);
                    var fallback = unescape(m.group(2));
                    assertTrue(es.containsKey(key), file.getFileName() + ": falta la clave " + key);
                    assertTrue(fallback.equals(es.get(key)) || fallback.equals(en.get(key)),
                        file.getFileName() + ": el respaldo de " + key + " no coincide con es_es ni con en_us: " + fallback);
                    checked++;
                }
            }
        }
        assertTrue(checked > 100, "Se esperaban muchas llamadas con respaldo, hay " + checked);
    }

    /** Las claves construidas por id (monedas, estadisticas) tambien llevan el espanol de es_es. */
    @Test void idBuiltKeysMatchSpanish() throws Exception {
        var es = load("es_es.json");
        UiText.INSTANCE.getFallbacks().forEach((key, text) -> assertEquals(es.get(key), text, key));
        assertTrue(UiText.INSTANCE.getFallbacks().size() >= 36);
    }
}
