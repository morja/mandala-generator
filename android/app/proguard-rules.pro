# R8 rules for the release build (minify and resource shrinking).
#
# kotlinx.serialization and the Google Play Billing Library bring their own rules in their jar and
# aar, which R8 applies on its own. The app and `core` look up every serializer explicitly
# (`MandalaParameters.serializer()` and so on), never by reflection, so no extra keep rules are
# needed for the settings JSON. Checked on 20 October 2026 by running the release build: starter
# favourites, last settings and history load, and the Pro sheet opens.

# Readable stack traces in the Play Console (the mapping file goes into the app bundle).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
