# R8 / ProGuard Rules for VoidPlayer

# Media3 is not accessed by reflection; retain only MediaSessionService entry points.
-keep class com.tushar.voidplayer.player.PlaybackService { *; }

# Keep Android components and serializers used by platform APIs.
-keep class com.tushar.voidplayer.VoidPlayerApp { *; }

# jaudiotagger's optional desktop artwork conversion helpers reference AWT/ImageIO.
# Android uses binaryData only; these classes are not present on Android and those
# desktop-only helpers are not called by the app.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream
