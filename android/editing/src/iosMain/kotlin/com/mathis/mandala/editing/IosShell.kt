package com.mathis.mandala.editing

import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.IosBridge
import com.mathis.mandala.core.MacFile
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.RenderTicket
import com.mathis.mandala.core.Rendered
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.ImageLoader
import com.mathis.mandala.editing.Editing.newVariation
import com.mathis.mandala.editing.Randomize.randomAll
import com.mathis.mandala.editing.Randomize.randomLayers
import kotlin.random.Random

/** Which new mandala the menu's dice make (Android's `NewKind`). */
enum class NewKind { VARIATION, LAYERS, EVERYTHING }

/** What a settings file or a Mac favourites file holds (Android's `MacFile`, flattened for Swift). */
class OpenedFile(val settings: MandalaParameters?, val favorites: List<Favorite>?)

/** The result of merging an imported favourites file. */
class FavoritesMerged(val list: List<Favorite>, val added: Int)

/** The play field's state that Swift keeps between touches: the landscape belongs to the middle it was made around. */
class PlayLandscape internal constructor(internal val land: PlayField.Landscape)

/**
 * The iPhone shell's door to the shared history, die, play field and favourites rules (WP-7), in plain
 * types for Swift. Every rule is the shared module's; none is written twice.
 */
object IosShell {
    // ── History (the shared History, kept in the Mac's history.json format) ──

    fun makeHistory(saved: List<MandalaParameters>): IosHistory = IosHistory(saved)

    fun decodeHistory(json: String): List<MandalaParameters> =
        runCatching { SettingsJson.decodeList(json) }.getOrDefault(emptyList())

    fun encodeHistory(list: List<MandalaParameters>): String = SettingsJson.encodeList(list)

    // ── The dice ──

    private fun seed(): ULong = Random.nextLong().toULong()

    /** Surprise mandalas stay within the free line without Pro, so they can always be kept. */
    fun surpriseLayers(pro: Boolean): Int = if (pro) Randomize.MAX_LAYERS else ProFeatures.FREE_LAYERS

    fun nextMandala(current: MandalaParameters, kind: NewKind, pro: Boolean): MandalaParameters = when (kind) {
        NewKind.VARIATION -> current.newVariation(::seed)
        NewKind.LAYERS -> current.randomLayers(Random, surpriseLayers(pro))
        NewKind.EVERYTHING -> current.randomAll(Random, surpriseLayers(pro))
    }

    /** The top bar's die: "Surprise me" (Android's `surprise`). */
    fun surprise(current: MandalaParameters, pro: Boolean): MandalaParameters = current.randomAll(Random, surpriseLayers(pro))

    /** A small picture of a new mandala for the menu's comparison (Android: `Editing.CANDIDATE_SIZE`, fast glow). */
    fun renderCandidate(params: MandalaParameters, fast: Boolean, threads: Int, ticket: RenderTicket, loader: ImageLoader?): Rendered? = try {
        val size = Editing.CANDIDATE_SIZE
        val result = MandalaRenderer.render(
            params,
            RenderOptions(outputSize = size, threads = threads, isCancelled = ticket::isCancelled, imageLoader = loader, glowReferenceWidth = if (fast) size * 2 else RenderOptions().glowReferenceWidth),
        )
        Rendered(IosBridge.rgba(result.image), size, size, 0)
    } catch (e: RenderCancelledException) {
        null
    }

    // ── Favourites ──

    fun addFavorite(list: List<Favorite>, favorite: Favorite): List<Favorite> = Favorites.add(list, favorite)

    fun removeFavorite(list: List<Favorite>, id: String): List<Favorite> = Favorites.remove(list, id)

    fun mergeFavorites(list: List<Favorite>, imported: List<Favorite>): FavoritesMerged =
        Favorites.merge(list, imported).let { FavoritesMerged(it.list, it.added) }

    /** A settings file or a favourites file of the Mac app (or of this app); null when it is neither. */
    fun decodeFile(text: String): OpenedFile? = when (val file = runCatching { SettingsJson.decodeMacFile(text) }.getOrNull()) {
        is MacFile.Settings -> OpenedFile(file.params, null)
        is MacFile.Favorites -> OpenedFile(null, file.favorites)
        null -> null
    }

    // ── The play field ──

    fun playMiddleHop(touchX: Double, touchY: Double): List<Double> =
        PlayField.hopOutOfTheMiddle(PlayField.Touch(touchX, touchY), Random).let { listOf(it.x, it.y) }

    fun makeLandscape(base: MandalaParameters): PlayLandscape =
        PlayLandscape(PlayField.Landscape(Random.nextLong(), base, BuiltInPalettes.all.size))

    fun play(base: MandalaParameters, compass: Boolean, land: PlayLandscape, x: Double, y: Double, effects: Boolean): MandalaParameters =
        PlayField.apply(base, mode(compass), land.land, PlayField.Touch(x, y), effects)

    fun playMiddle(base: MandalaParameters, effects: Boolean): MandalaParameters = PlayField.middle(base, effects)

    fun playMiddleFrom(base: MandalaParameters, compass: Boolean, land: PlayLandscape, x: Double, y: Double, effects: Boolean): MandalaParameters =
        PlayField.newMiddle(base, mode(compass), land.land, PlayField.Touch(x, y), effects)

    private fun mode(compass: Boolean) = if (compass) PlayField.Mode.COMPASS else PlayField.Mode.MAP
}

/** The shared [History] with plain types for Swift. */
class IosHistory(saved: List<MandalaParameters>) {
    private val history = History<MandalaParameters>().apply { restore(saved) }

    val canGoBack: Boolean get() = history.canGoBack
    val canGoForward: Boolean get() = history.canGoForward
    val entries: List<MandalaParameters> get() = history.entries

    fun record(state: MandalaParameters) = history.record(state)
    fun back(): MandalaParameters? = history.back()
    fun forward(): MandalaParameters? = history.forward()
}
