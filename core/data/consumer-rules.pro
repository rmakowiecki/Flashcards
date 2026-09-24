# Firestore's toObject() maps documents onto these classes by reflection (no-arg constructor plus
# property setters), so R8 must neither strip nor rename their members. Without this, release
# builds read documents into default-valued objects instead of crashing.
-keep class com.rossomak.flashcards.core.data.model.** {
    <init>();
    *;
}
