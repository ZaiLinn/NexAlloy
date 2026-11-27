package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.TranslatedCaptionPensPatch
import io.github.nexalloy.patch
import java.io.InputStream

val TranslatedCaptionPens = patch(
    name = "Translated caption pens",
    description = "Fixes auto-translated captions of styled caption tracks showing an empty background.",
) {
    // Adds the caption track interface used to track the parsed track.
    dependsOn(AutoTranslateCaptions)

    // Track whether the caption track that is parsed is auto-translated.
    // The track is set from the request being parsed and not from the last requested url,
    // because cached tracks are parsed without a new request.
    LanguageCaptionFileParserFingerprint.hookMethod {
        before {
            TranslatedCaptionPensPatch.setParsedRequest(wrapRequest(it.args[0]))
        }
    }

    // Network responses are parsed without a cached caption track file.
    val requestField = ::requestResponseRequestField.field.accessible()
    RequestResponseParserFingerprint.hookMethod {
        before {
            val request = runCatching { requestField.get(it.thisObject) }.getOrNull()

            TranslatedCaptionPensPatch.setParsedRequest(wrapRequest(request))
        }
    }

    // Translated automatic captions can have orphan line break events,
    // and stable caption blocks use the source timing of automatic captions.
    // Every caption decode goes through the same parser entry.
    ::timedTextParseMethod.hookMethod {
        before {
            it.args[0] = TranslatedCaptionPensPatch.repairInput(it.args[0] as? InputStream)
        }
    }

    // Every pen lookup of the timed text parser (<pen>, <p> and <s> elements) uses the same method.
    // Auto-translated captions ignore the pens, so they use the user caption style.
    ::captionPenLookupMethod.hookMethod {
        before {
            it.args[0] = TranslatedCaptionPensPatch.getPenId(it.args[0] as Int)
        }
    }
}
