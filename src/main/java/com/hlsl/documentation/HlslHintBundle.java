package com.hlsl.documentation;

import com.hlsl.validation.HlslDxcSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The text of a hover explanation, read from a resource bundle in the language chosen on the settings page,
 * so the same explanation is available in English and in Chinese.
 */
final class HlslHintBundle {

    private final String baseName;
    /** The default bundle, which holds the English text. */
    private final ResourceBundle base;

    HlslHintBundle(@NotNull String baseName) {
        this.baseName = baseName;
        this.base = ResourceBundle.getBundle(baseName, Locale.ROOT, NO_DEFAULT_FALLBACK);
    }

    /** The text of {@code key} in the configured language, falling back to English, or {@code null}. */
    @Nullable String text(@NotNull String key) {
        ResourceBundle bundle = bundle();
        try {
            if (bundle.containsKey(key)) return bundle.getString(key);
        } catch (MissingResourceException ignored) {
            // Fall through to the default bundle below.
        }
        return base.containsKey(key) ? base.getString(key) : null;
    }

    /** The language the text is read in: the configured one, or the IDE's own by default. */
    static @NotNull Locale locale() {
        String configured = HlslDxcSettings.getInstance().getHintLanguage();
        if (configured == null || configured.isBlank() || "auto".equals(configured)) return Locale.getDefault();
        return Locale.forLanguageTag(configured);
    }

    /**
     * The bundle for the configured language, falling back to the default (English) one.
     *
     * <p>{@link ResourceBundle#getBundle(String, Locale)} cannot be used on its own: asked for a language the
     * plugin does not ship, it falls back to {@link Locale#getDefault()} before it falls back to the base
     * bundle, so on a Chinese machine "English" would have answered in Chinese.</p>
     */
    private @NotNull ResourceBundle bundle() {
        Locale locale = locale();
        if (locale.getLanguage().isEmpty() || "en".equals(locale.getLanguage())) return base;
        return ResourceBundle.getBundle(baseName, locale, NO_DEFAULT_FALLBACK);
    }

    /**
     * Resolves only the language asked for: no fallback to the JVM's own locale, and no key inheritance from
     * the parent, which belongs to the default locale here.
     */
    private static final class NoDefaultFallbackControl extends ResourceBundle.Control {
        @Override
        public List<Locale> getCandidateLocales(String baseName, Locale locale) {
            List<Locale> candidates = super.getCandidateLocales(baseName, locale);
            // The last candidate is the base bundle, which the chain resolves on its own.
            return candidates.size() > 1 ? candidates.subList(0, candidates.size() - 1) : candidates;
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        @Override
        public boolean needsReload(String baseName, Locale locale, String format, ClassLoader loader,
                                   ResourceBundle bundle, long loadTime) {
            return false;
        }
    }

    private static final ResourceBundle.Control NO_DEFAULT_FALLBACK = new NoDefaultFallbackControl();
}
