# Profiling builds keep R8's shrinking and optimization, so they perform like release, but skip
# renaming so stack traces and traces read without a mapping file.
-dontobfuscate
