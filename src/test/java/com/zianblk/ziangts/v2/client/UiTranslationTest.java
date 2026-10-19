package com.zianblk.ziangts.v2.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class UiTranslationTest {
    private static Language language(JsonObject values) {
        return new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.has(key) ? values.get(key).getAsString() : fallback; }
            @Override public boolean has(String key) { return values.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), Style.EMPTY); }
        };
    }
    private void verify(String locale) throws Exception {
        JsonObject values;
        try(var stream=UiText.class.getResourceAsStream("/assets/ziangts/lang/"+locale+".json")) {
            assertNotNull(stream,"Packaged language resource is missing");
            values=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var previous=Language.getInstance();
        try {
            Language.inject(language(values));
            assertEquals("Página 1/1",UiText.INSTANCE.pageCounter(1,0).getString());
            assertEquals("Página 2/5",UiText.INSTANCE.pageCounter(2,5).getString());
            assertEquals("Página 5/5",UiText.INSTANCE.pageCounter(5,5).getString());
            assertEquals("Hembra · Nivel 7",UiText.INSTANCE.genderLevel("Hembra",7).getString());
            assertEquals("PS: 07/31",UiText.INSTANCE.ivValue("PS",7).getString());
            assertEquals("Ataque: 31/31",UiText.INSTANCE.ivValue("Ataque",31).getString());
            for(var entry:values.entrySet())assertFalse(entry.getValue().getAsString().matches(".*\\$(?:\\{|[A-Za-z_]).*"),"Unexpanded variable in "+entry.getKey());
        } finally { Language.inject(previous); }
    }
    @Test void spanishSpainUsesRuntimeValues() throws Exception { verify("es_es"); }
    @Test void spanishMexicoUsesRuntimeValues() throws Exception { verify("es_mx"); }
    @Test void missingTranslationStillFormatsFallback() {
        var previous=Language.getInstance();
        try { Language.inject(language(new JsonObject())); assertEquals("Página 1/1",UiText.INSTANCE.pageCounter(0,0).getString()); }
        finally { Language.inject(previous); }
    }
}
